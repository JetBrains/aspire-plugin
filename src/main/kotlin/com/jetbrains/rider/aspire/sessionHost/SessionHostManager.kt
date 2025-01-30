package com.jetbrains.rider.aspire.sessionHost

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.jetbrains.rd.framework.util.setSuspend
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.threading.coroutines.lifetimedCoroutineScope
import com.jetbrains.rider.aspire.generated.*
import com.jetbrains.rider.aspire.run.AspireHostConfig
import com.jetbrains.rider.aspire.sessionHost.SessionManager.CreateSessionCommand
import com.jetbrains.rider.aspire.sessionHost.SessionManager.DeleteSessionCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import java.util.*

@Service(Service.Level.PROJECT)
class SessionHostManager(private val project: Project, private val scope: CoroutineScope) {
    companion object {
        fun getInstance(project: Project) = project.service<SessionHostManager>()

        private val LOG = logger<SessionHostManager>()
    }

    fun startSessionHost(
        aspireHostConfig: AspireHostConfig,
        protocolServerPort: Int,
        sessionHostModel: AspireSessionHostModel
    ) {
        LOG.trace { "Creating a new Aspire session host: $aspireHostConfig" }

        val sessionHostLifetime = aspireHostConfig.aspireHostLifetime.createNested()

        subscribe(aspireHostConfig, sessionHostModel, sessionHostLifetime)

        LOG.trace("Starting a new session host with launcher")
        val sessionHostLauncher = SessionHostLauncher.getInstance(project)
        sessionHostLauncher.launchSessionHost(
            aspireHostConfig,
            protocolServerPort,
            sessionHostLifetime
        )
    }

    private fun subscribe(
        aspireHostConfig: AspireHostConfig,
        sessionHostModel: AspireSessionHostModel,
        sessionHostLifetime: Lifetime
    ) {
        LOG.trace("Subscribing to protocol model")
        val sessionEvents = MutableSharedFlow<SessionEvent>(
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
            extraBufferCapacity = 100,
            replay = 20
        )

        scope.launch(Dispatchers.EDT) {
            sessionHostModel.createSession.setSuspend { _, model ->
                createSession(model, sessionEvents, aspireHostConfig, sessionHostLifetime)
            }

            sessionHostModel.deleteSession.setSuspend { _, sessionId ->
                deleteSession(sessionId)
            }

            lifetimedCoroutineScope(sessionHostLifetime) {
                sessionEvents.collect {
                    when (it) {
                        is SessionStarted -> {
                            LOG.trace { "Aspire session started (${it.id}, ${it.pid})" }
                            sessionHostModel.processStarted.fire(ProcessStarted(it.id, it.pid))
                        }

                        is SessionTerminated -> {
                            LOG.trace { "Aspire session terminated (${it.id}, ${it.exitCode})" }
                            sessionHostModel.processTerminated.fire(ProcessTerminated(it.id, it.exitCode))
                        }

                        is SessionLogReceived -> {
                            LOG.trace { "Aspire session log received (${it.id}, ${it.isStdErr}, ${it.message})" }
                            sessionHostModel.logReceived.fire(LogReceived(it.id, it.isStdErr, it.message))
                        }
                    }
                }
            }
        }
    }

    private suspend fun createSession(
        sessionModel: SessionModel,
        sessionEvents: MutableSharedFlow<SessionEvent>,
        aspireHostConfig: AspireHostConfig,
        sessionHostLifetime: Lifetime
    ): SessionCreationResult {
        val sessionId = UUID.randomUUID().toString()

        LOG.trace { "Creating session with id: $sessionId" }

        val command = CreateSessionCommand(
            sessionId,
            sessionModel,
            sessionEvents,
            aspireHostConfig,
            sessionHostLifetime
        )

        SessionManager.getInstance(project).submitCommand(command)

        return SessionCreationResult(sessionId)
    }

    private suspend fun deleteSession(sessionId: String): Boolean {
        LOG.trace { "Deleting session with id: $sessionId" }

        val command = DeleteSessionCommand(sessionId)

        SessionManager.getInstance(project).submitCommand(command)

        return true
    }
}