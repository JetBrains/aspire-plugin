package me.rafaelldi.aspire.sessionHost

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.threading.coroutines.lifetimedCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import me.rafaelldi.aspire.generated.AspireSessionHostModel
import me.rafaelldi.aspire.generated.LogReceived
import me.rafaelldi.aspire.generated.ProcessStarted
import me.rafaelldi.aspire.generated.ProcessTerminated
import me.rafaelldi.aspire.run.AspireHostConfig

@Service(Service.Level.PROJECT)
class SessionHostManager(private val project: Project, private val scope: CoroutineScope) {
    companion object {
        fun getInstance(project: Project) = project.service<SessionHostManager>()

        private val LOG = logger<SessionHostManager>()
    }

    suspend fun startSessionHost(
        aspireHostConfig: AspireHostConfig,
        protocolServerPort: Int,
        sessionHostModel: AspireSessionHostModel
    ) {
        LOG.trace("Adding Aspire session host: $aspireHostConfig")

        val sessionHostLifetime = aspireHostConfig.aspireHostLifetime.createNested()

        val sessionEvents = MutableSharedFlow<SessionEvent>(
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
            extraBufferCapacity = 100
        )
        subscribe(sessionHostModel, sessionEvents, sessionHostLifetime)

        val sessionManager = SessionManager.getInstance(project)
        sessionManager.addSessionHost(
            aspireHostConfig,
            sessionHostModel,
            sessionEvents,
            sessionHostLifetime
        )

        LOG.trace("Starting new session hosts with launcher")
        val sessionHostLauncher = SessionHostLauncher.getInstance(project)
        sessionHostLauncher.launchSessionHost(
            aspireHostConfig,
            protocolServerPort,
            sessionHostLifetime
        )
    }

    private suspend fun subscribe(
        sessionHostModel: AspireSessionHostModel,
        sessionEvents: SharedFlow<SessionEvent>,
        aspireHostLifetime: Lifetime
    ) {
        LOG.trace("Subscribing to protocol model")
        scope.launch(Dispatchers.EDT) {
            lifetimedCoroutineScope(aspireHostLifetime) {
                sessionEvents.collect {
                    when (it) {
                        is SessionStarted -> {
                            LOG.trace("Aspire session started (${it.id}, ${it.pid})")
                            sessionHostModel.processStarted.fire(ProcessStarted(it.id, it.pid))
                        }

                        is SessionTerminated -> {
                            LOG.trace("Aspire session terminated (${it.id}, ${it.exitCode})")
                            sessionHostModel.processTerminated.fire(ProcessTerminated(it.id, it.exitCode))
                        }

                        is SessionLogReceived -> {
                            LOG.trace("Aspire session log received (${it.id}, ${it.isStdErr}, ${it.message})")
                            sessionHostModel.logReceived.fire(LogReceived(it.id, it.isStdErr, it.message))
                        }
                    }
                }
            }
        }
    }
}