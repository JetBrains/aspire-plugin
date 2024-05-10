@file:OptIn(DelicateCoroutinesApi::class)

package me.rafaelldi.aspire.sessionHost

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.SequentialLifetimes
import com.jetbrains.rd.util.threading.coroutines.lifetimedCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import me.rafaelldi.aspire.generated.AspireSessionHostModel
import me.rafaelldi.aspire.generated.LogReceived
import me.rafaelldi.aspire.generated.ProcessStarted
import me.rafaelldi.aspire.generated.ProcessTerminated
import me.rafaelldi.aspire.generated.SessionCreationResult
import me.rafaelldi.aspire.generated.SessionModel
import me.rafaelldi.aspire.run.AspireHostConfig
import me.rafaelldi.aspire.sessionHost.SessionManager.CreateSessionCommand
import me.rafaelldi.aspire.sessionHost.SessionManager.DeleteSessionCommand
import java.util.UUID

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

        subscribe(aspireHostConfig, sessionHostModel, sessionHostLifetime)

        LOG.trace("Starting new session hosts with launcher")
        val sessionHostLauncher = SessionHostLauncher.getInstance(project)
        sessionHostLauncher.launchSessionHost(
            aspireHostConfig,
            protocolServerPort,
            sessionHostLifetime
        )
    }

    private suspend fun subscribe(
        aspireHostConfig: AspireHostConfig,
        sessionHostModel: AspireSessionHostModel,
        sessionHostLifetime: Lifetime
    ) {
        LOG.trace("Subscribing to protocol model")
        val sessionEvents = MutableSharedFlow<SessionEvent>(
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
            extraBufferCapacity = 100
        )

        scope.launch(Dispatchers.EDT) {
            sessionHostModel.createSession.set { model ->
                createSession(model, sessionEvents, aspireHostConfig, sessionHostLifetime)
            }

            sessionHostModel.deleteSession.set { sessionId ->
                deleteSession(sessionId)
            }

            lifetimedCoroutineScope(sessionHostLifetime) {
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

    private fun createSession(
        sessionModel: SessionModel,
        sessionEvents: MutableSharedFlow<SessionEvent>,
        aspireHostConfig: AspireHostConfig,
        sessionHostLifetime: Lifetime
    ): SessionCreationResult {
        val sessionId = UUID.randomUUID().toString()

        val command = CreateSessionCommand(
            sessionId,
            sessionModel,
            sessionEvents,
            aspireHostConfig,
            SequentialLifetimes(sessionHostLifetime)
        )

        SessionManager.getInstance(project).commands.tryEmit(command)

        return SessionCreationResult(sessionId)
    }

    private fun deleteSession(sessionId: String): Boolean {
        val command = DeleteSessionCommand(sessionId)

        SessionManager.getInstance(project).commands.tryEmit(command)

        return true
    }
}