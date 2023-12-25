package me.rafaelldi.aspire.sessionHost

import com.intellij.execution.services.ServiceEventListener
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.util.launchOnUi
import com.intellij.openapi.rd.util.withUiContext
import com.jetbrains.rd.framework.*
import com.jetbrains.rd.util.addUnique
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rd.util.lifetime.isNotAlive
import com.jetbrains.rdclient.protocol.RdDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import me.rafaelldi.aspire.generated.*
import me.rafaelldi.aspire.otel.OtelMetricService
import me.rafaelldi.aspire.services.AspireServiceContributor
import me.rafaelldi.aspire.services.SessionHostServiceData
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class AspireSessionHostManager(private val project: Project) {
    companion object {
        fun getInstance(project: Project) = project.service<AspireSessionHostManager>()

        private val LOG = logger<AspireSessionHostManager>()
    }

    private val sessionHosts = ConcurrentHashMap<String, SessionHostServiceData>()
    private val serviceEventPublisher = project.messageBus.syncPublisher(ServiceEventListener.TOPIC)
    private val aspireSessionEventPublisher = project.messageBus.syncPublisher(AspireSessionListener.TOPIC)

    fun getSessionHosts() = sessionHosts.values.toList()

    suspend fun runSessionHost(
        sessionHostConfig: AspireSessionHostConfig,
        sessionHostLifetime: LifetimeDefinition
    ) {
        LOG.info("Starting Aspire session host: $sessionHostConfig")

        if (sessionHostLifetime.isNotAlive) {
            LOG.warn("Unable to start Aspire host because lifetime is not alive")
            return
        }

        LOG.trace("Starting protocol")
        val protocol = startProtocol(sessionHostLifetime)
        val sessionHostModel = protocol.aspireSessionHostModel

        LOG.trace("Subscribing to protocol model")
        subscribe(sessionHostConfig, sessionHostModel, sessionHostLifetime)

        LOG.trace("Starting new session hosts with runner")
        val sessionHostRunner = AspireSessionHostRunner.getInstance(project)
        sessionHostRunner.runSessionHost(
            sessionHostConfig,
            protocol.wire.serverPort,
            sessionHostLifetime
        )

        val sessionHostData = SessionHostServiceData(
            sessionHostConfig.id,
            sessionHostConfig.hostName,
            sessionHostConfig.dashboardUrl,
            sessionHostModel,
            sessionHostLifetime.lifetime
        )
        LOG.trace("Adding new session host data $sessionHostData")
        sessionHosts.addUnique(sessionHostLifetime, sessionHostConfig.id, sessionHostData)

        sessionHostLifetime.bracketIfAlive(
            {
                val event = ServiceEventListener.ServiceEvent.createResetEvent(AspireServiceContributor::class.java)
                serviceEventPublisher.handle(event)
            },
            {
                val event = ServiceEventListener.ServiceEvent.createResetEvent(AspireServiceContributor::class.java)
                serviceEventPublisher.handle(event)
            }
        )
    }

    private suspend fun startProtocol(lifetime: Lifetime) = withUiContext {
        val dispatcher = RdDispatcher(lifetime)
        val wire = SocketWire.Server(lifetime, dispatcher, null)
        val protocol = Protocol(
            "AspireSessionHost::protocol",
            Serializers(),
            Identities(IdKind.Server),
            dispatcher,
            wire,
            lifetime
        )
        return@withUiContext protocol
    }

    private suspend fun subscribe(
        sessionHostConfig: AspireSessionHostConfig,
        sessionHostModel: AspireSessionHostModel,
        sessionHostLifetime: Lifetime
    ) {
        val sessionEvents = Channel<AspireSessionEvent>(Channel.UNLIMITED)
        sessionHostLifetime.launchOnUi {
            sessionEvents.consumeAsFlow().collect {
                when (it) {
                    is AspireSessionStarted -> {
                        LOG.trace("Aspire session started (${it.id}, ${it.pid})")
                        sessionHostModel.processStarted.fire(ProcessStarted(it.id, it.pid))
                    }

                    is AspireSessionTerminated -> {
                        LOG.trace("Aspire session terminated (${it.id}, ${it.exitCode})")
                        sessionHostModel.processTerminated.fire(ProcessTerminated(it.id, it.exitCode))
                    }

                    is AspireSessionLogReceived -> {
                        LOG.trace("Aspire session log received (${it.id}, ${it.isStdErr}, ${it.message})")
                        sessionHostModel.logReceived.fire(LogReceived(it.id, it.isStdErr, it.message))
                    }
                }
            }
        }

        withUiContext {
            val service = OtelMetricService.getInstance(project)
            sessionHostModel.otelMetricReceived.advise(sessionHostLifetime) {
                service.metricReceived(it)
            }

            sessionHostModel.sessions.view(sessionHostLifetime) { sessionLifetime, sessionId, sessionModel ->
                LOG.info("New session added $sessionId, $sessionModel")
                runSession(sessionId, sessionModel, sessionLifetime, sessionEvents, sessionHostConfig)
            }
        }
    }

    private fun runSession(
        sessionId: String,
        sessionModel: SessionModel,
        sessionLifetime: Lifetime,
        sessionEvents: Channel<AspireSessionEvent>,
        sessionHostConfig: AspireSessionHostConfig
    ) {
        val command = AspireSessionRunner.RunSessionCommand(
            sessionId,
            sessionModel,
            sessionLifetime,
            sessionEvents,
            sessionHostConfig.hostName,
            sessionHostConfig.isDebug,
            sessionHostConfig.openTelemetryPort
        )

        LOG.trace("Starting new session with runner (command $command)")
        val runner = AspireSessionRunner.getInstance(project)
        runner.runSession(command)

        sessionLifetime.bracketIfAlive(
            {
                val event = ServiceEventListener.ServiceEvent.createResetEvent(AspireServiceContributor::class.java)
                serviceEventPublisher.handle(event)

                if (sessionModel.telemetryServiceName != null)
                    aspireSessionEventPublisher.sessionStarted(sessionModel.telemetryServiceName)
            },
            {
                val event = ServiceEventListener.ServiceEvent.createResetEvent(AspireServiceContributor::class.java)
                serviceEventPublisher.handle(event)

                if (sessionModel.telemetryServiceName != null)
                    aspireSessionEventPublisher.sessionTerminated(sessionModel.telemetryServiceName)
            }
        )
    }
}