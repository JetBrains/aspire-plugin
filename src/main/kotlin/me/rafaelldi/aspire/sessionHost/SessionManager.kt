package me.rafaelldi.aspire.sessionHost

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.application
import com.jetbrains.rd.framework.util.setSuspend
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rafaelldi.aspire.generated.AspireSessionHostModel
import me.rafaelldi.aspire.generated.SessionCreationResult
import me.rafaelldi.aspire.generated.SessionModel
import me.rafaelldi.aspire.run.AspireHostProjectConfig
import java.util.*

@Service(Service.Level.PROJECT)
class SessionManager(private val project: Project, scope: CoroutineScope) {
    companion object {
        fun getInstance(project: Project) = project.service<SessionManager>()

        private val LOG = logger<SessionManager>()
    }

    private val sessions = mutableMapOf<String, LifetimeDefinition>()

    private val commands = MutableSharedFlow<LaunchSessionCommand>(
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
        extraBufferCapacity = 100
    )

    init {
        scope.launch {
            commands.collect { handleCommand(it) }
        }
    }

    suspend fun addSessionHost(
        aspireHostConfig: AspireHostProjectConfig,
        sessionHostModel: AspireSessionHostModel,
        sessionEvents: MutableSharedFlow<SessionEvent>,
        sessionHostLifetime: Lifetime
    ) {
        LOG.trace("Adding Aspire session host")

        withContext(Dispatchers.EDT) {
            sessionHostModel.createSession.setSuspend { _, model ->
                createSession(model, sessionEvents, aspireHostConfig, sessionHostLifetime)
            }

            sessionHostModel.deleteSession.setSuspend { _, sessionId ->
                deleteSession(sessionId)
            }
        }
    }

    private suspend fun createSession(
        sessionModel: SessionModel,
        sessionEvents: MutableSharedFlow<SessionEvent>,
        aspireHostConfig: AspireHostProjectConfig,
        sessionHostLifetime: Lifetime
    ): SessionCreationResult {
        val sessionId = UUID.randomUUID().toString()

        val command = CreateSessionCommand(
            sessionId,
            sessionModel,
            sessionEvents,
            aspireHostConfig,
            sessionHostLifetime
        )
        commands.emit(command)

        return SessionCreationResult(sessionId)
    }

    private suspend fun deleteSession(sessionId: String): Boolean {
        val command = DeleteSessionCommand(sessionId)
        commands.emit(command)

        return true
    }

    private suspend fun handleCommand(command: LaunchSessionCommand) {
        when (command) {
            is CreateSessionCommand -> handleCreateCommand(command)
            is DeleteSessionCommand -> handleDeleteCommand(command)
        }
    }

    private suspend fun handleCreateCommand(command: CreateSessionCommand) {
        LOG.trace("Creating session ${command.sessionId}, ${command.sessionModel}")

        val sessionLifetimeDef = command.sessionHostLifetime.createNested()
        sessions[command.sessionId] = sessionLifetimeDef

        val launcher = SessionLauncher.getInstance(project)
        launcher.launchSession(
            command.sessionId,
            command.sessionModel,
            sessionLifetimeDef.lifetime,
            command.sessionEvents,
            command.aspireHostConfig.debuggingMode,
            command.aspireHostConfig.openTelemetryProtocolServerPort
        )
    }

    private fun handleDeleteCommand(command: DeleteSessionCommand) {
        LOG.trace("Deleting session ${command.sessionId}")

        val lifetimes = sessions.remove(command.sessionId) ?: return

        application.invokeLater {
            lifetimes.terminate()
        }
    }

    interface LaunchSessionCommand

    data class CreateSessionCommand(
        val sessionId: String,
        val sessionModel: SessionModel,
        val sessionEvents: MutableSharedFlow<SessionEvent>,
        val aspireHostConfig: AspireHostProjectConfig,
        val sessionHostLifetime: Lifetime
    ) : LaunchSessionCommand

    data class DeleteSessionCommand(
        val sessionId: String
    ) : LaunchSessionCommand
}