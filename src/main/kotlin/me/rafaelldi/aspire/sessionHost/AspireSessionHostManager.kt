package me.rafaelldi.aspire.sessionHost

import com.intellij.execution.services.ServiceEventListener
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.util.launchOnUi
import com.intellij.openapi.rd.util.withUiContext
import com.jetbrains.rd.framework.*
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rd.util.lifetime.isNotAlive
import com.jetbrains.rdclient.protocol.RdDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import me.rafaelldi.aspire.generated.*
import me.rafaelldi.aspire.services.AspireHostServiceContributor
import me.rafaelldi.aspire.services.AspireServiceContributor
import me.rafaelldi.aspire.services.SessionHostServiceData
import me.rafaelldi.aspire.services.SessionServiceData
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class AspireSessionHostManager(private val project: Project) {
    companion object {
        fun getInstance(project: Project) = project.service<AspireSessionHostManager>()

        private val LOG = logger<AspireSessionHostManager>()
    }

    private val sessionHosts = ConcurrentHashMap<String, AspireHostServiceContributor>()
    private val sessionHostModels = ConcurrentHashMap<String, Pair<AspireSessionHostModel, Lifetime>>()
    private val sessions = ConcurrentHashMap<String, MutableMap<String, SessionServiceData>>()

    private val serviceEventPublisher = project.messageBus.syncPublisher(ServiceEventListener.TOPIC)

    fun isSessionHostAvailable(sessionHostId: String) = sessionHosts.containsKey(sessionHostId)
    fun getSessionHost(sessionHostId: String) = sessionHosts[sessionHostId]
    fun getSessionHosts() = sessionHosts.values.toList()
    fun getSessionHostModel(sessionHostId: String) = sessionHostModels[sessionHostId]
    fun getSessions(sessionHostId: String) = sessions[sessionHostId]?.values?.toList() ?: emptyList()

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

        val sessionHostData = SessionHostServiceData(
            sessionHostConfig.debugSessionToken,
            sessionHostConfig.runProfileName,
            sessionHostConfig.aspireHostProjectPath,
            sessionHostConfig.aspireHostProjectUrl,
            sessionHostModel,
            sessionHostLifetime.lifetime
        )

        sessionHostLifetime.bracketIfAlive({
            LOG.trace("Adding a new session host data $sessionHostData")
            val sessionHost = AspireHostServiceContributor(sessionHostData)
            sessionHosts[sessionHostConfig.debugSessionToken] = sessionHost
            sessionHostModels[sessionHostConfig.debugSessionToken] = sessionHostModel to sessionHostLifetime.lifetime
            sessions[sessionHostConfig.debugSessionToken] = mutableMapOf()
            val event = ServiceEventListener.ServiceEvent.createEvent(
                ServiceEventListener.EventType.SERVICE_ADDED,
                sessionHost,
                AspireServiceContributor::class.java
            )
            serviceEventPublisher.handle(event)
        }, {
            LOG.trace("Removing the session host data ${sessionHostConfig.debugSessionToken}")
            val sessionHost = sessionHosts.remove(sessionHostConfig.debugSessionToken)
            sessionHostModels.remove(sessionHostConfig.debugSessionToken)
            sessions.remove(sessionHostConfig.debugSessionToken)
            if (sessionHost == null) return@bracketIfAlive
            val event = ServiceEventListener.ServiceEvent.createEvent(
                ServiceEventListener.EventType.SERVICE_REMOVED,
                sessionHost,
                AspireServiceContributor::class.java
            )
            serviceEventPublisher.handle(event)
        })

        LOG.trace("Starting new session hosts with runner")
        val sessionHostRunner = AspireSessionHostRunner.getInstance(project)
        sessionHostRunner.runSessionHost(
            sessionHostConfig,
            protocol.wire.serverPort,
            sessionHostLifetime
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
            sessionHostModel.sessions.view(sessionHostLifetime) { sessionLifetime, sessionId, sessionModel ->
                LOG.info("New session added $sessionId, $sessionModel")
                viewSessions(sessionId, sessionModel, sessionLifetime, sessionEvents, sessionHostConfig)
            }
        }
    }

    private fun viewSessions(
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
            sessionHostConfig.runProfileName,
            sessionHostConfig.isDebug,
            sessionHostConfig.openTelemetryProtocolServerPort
        )

        val sessionHost = sessionHosts[sessionHostConfig.debugSessionToken] ?: return

        val sessionServiceData = SessionServiceData(
            sessionModel,
            sessionLifetime
        )

        sessionLifetime.bracketIfAlive({
            LOG.trace("Adding a new session data $sessionServiceData")
            val sessionsByHost = sessions[sessionHostConfig.debugSessionToken] ?: return@bracketIfAlive
            sessionsByHost[sessionId] = sessionServiceData
            val event = ServiceEventListener.ServiceEvent.createEvent(
                ServiceEventListener.EventType.SERVICE_STRUCTURE_CHANGED,
                sessionHost,
                AspireServiceContributor::class.java
            )
            project.messageBus.syncPublisher(ServiceEventListener.TOPIC).handle(event)
        }, {
            LOG.trace("Removing the session data ${sessionHostConfig.debugSessionToken}")
            val sessionsByHost = sessions[sessionHostConfig.debugSessionToken] ?: return@bracketIfAlive
            sessionsByHost.remove(sessionId) ?: return@bracketIfAlive
            val event = ServiceEventListener.ServiceEvent.createEvent(
                ServiceEventListener.EventType.SERVICE_STRUCTURE_CHANGED,
                sessionHost,
                AspireServiceContributor::class.java
            )
            project.messageBus.syncPublisher(ServiceEventListener.TOPIC).handle(event)
        })

        LOG.trace("Starting new session with runner (command $command)")
        val runner = AspireSessionRunner.getInstance(project)
        runner.runSession(command)
    }
}