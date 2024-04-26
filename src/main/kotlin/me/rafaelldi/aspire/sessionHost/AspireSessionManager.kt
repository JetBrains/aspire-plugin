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
class AspireSessionManager(private val project: Project, scope: CoroutineScope) {
    companion object {
        fun getInstance(project: Project) = project.service<AspireSessionManager>()

        private val LOG = logger<AspireSessionManager>()
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
        sessionEvents: MutableSharedFlow<AspireSessionEvent>,
        aspireHostLifetime: Lifetime
    ) {
        LOG.trace("Adding session host for ${aspireHostConfig.debugSessionToken}")

        withContext(Dispatchers.EDT) {
            sessionHostModel.createSession.setSuspend { _, model ->
                createSession(model, sessionEvents, aspireHostConfig, aspireHostLifetime)
            }

            sessionHostModel.deleteSession.setSuspend { _, sessionId ->
                deleteSession(sessionId, aspireHostConfig)
            }
        }
    }

    private suspend fun createSession(
        sessionModel: SessionModel,
        sessionEvents: MutableSharedFlow<AspireSessionEvent>,
        aspireHostConfig: AspireHostProjectConfig,
        aspireHostLifetime: Lifetime
    ): SessionCreationResult {
        val sessionId = UUID.randomUUID().toString()

        val command = CreateSessionCommand(
            sessionId,
            sessionModel,
            sessionEvents,
            aspireHostConfig,
            aspireHostLifetime
        )
        commands.emit(command)

        return SessionCreationResult(sessionId)
    }

    private suspend fun deleteSession(sessionId: String, aspireHostConfig: AspireHostProjectConfig): Boolean {
        val command = DeleteSessionCommand(sessionId, aspireHostConfig.debugSessionToken)
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

        val sessionLifetimeDef = command.aspireHostLifetime.createNested()
        sessions[command.sessionId] = sessionLifetimeDef

        val launcher = AspireSessionLauncher.getInstance(project)
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
        val sessionEvents: MutableSharedFlow<AspireSessionEvent>,
        val aspireHostConfig: AspireHostProjectConfig,
        val aspireHostLifetime: Lifetime
    ) : LaunchSessionCommand

    data class DeleteSessionCommand(
        val sessionId: String,
        val sessionHostId: String
    ) : LaunchSessionCommand
}