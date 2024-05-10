package me.rafaelldi.aspire.sessionHost

import com.intellij.database.util.common.removeIf
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.application
import com.jetbrains.rd.util.lifetime.SequentialLifetimes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rafaelldi.aspire.generated.SessionModel
import me.rafaelldi.aspire.run.AspireHostConfig

@Service(Service.Level.PROJECT)
class SessionManager(private val project: Project, scope: CoroutineScope) {
    companion object {
        fun getInstance(project: Project) = project.service<SessionManager>()

        private val LOG = logger<SessionManager>()
    }

    private val sessions = mutableMapOf<String, Session>()
    private val resourceToSessionMap = mutableMapOf<String, String>()

    private val commands = MutableSharedFlow<LaunchSessionCommand>(
        onBufferOverflow = BufferOverflow.SUSPEND,
        extraBufferCapacity = 100,
        replay = 100
    )

    init {
        scope.launch {
            commands.collect { handleCommand(it) }
        }
    }

    private suspend fun handleCommand(command: LaunchSessionCommand) {
        when (command) {
            is CreateSessionCommand -> handleCreateCommand(command)
            is DeleteSessionCommand -> handleDeleteCommand(command)
        }
    }

    private suspend fun handleCreateCommand(command: CreateSessionCommand) {
        LOG.trace("Creating session ${command.sessionId}, ${command.sessionModel}")

        val session = Session(
            command.sessionId,
            command.sessionModel,
            command.sessionHostLifetimes,
            command.sessionEvents,
            command.aspireHostConfig.openTelemetryProtocolServerPort
        )
        sessions[command.sessionId] = session

        saveConnectionToResource(command)

        val launcher = SessionLauncher.getInstance(project)
        launcher.launchSession(
            session.id,
            session.model,
            session.lifetimes.next(),
            session.events,
            command.aspireHostConfig.debuggingMode,
            session.openTelemetryProtocolServerPort
        )
    }

    private fun saveConnectionToResource(command: CreateSessionCommand) {
        val resourceAttributes =
            command.sessionModel.envs?.firstOrNull { it.key.equals("OTEL_RESOURCE_ATTRIBUTES", true) }?.value ?: return
        val serviceInstanceId =
            resourceAttributes.split(",").firstOrNull { it.startsWith("service.instance.id") } ?: return
        val idValue = serviceInstanceId.removePrefix("service.instance.id=")
        if (idValue.isEmpty()) return

        resourceToSessionMap[idValue] = command.sessionId
    }

    private fun handleDeleteCommand(command: DeleteSessionCommand) {
        LOG.trace("Deleting session ${command.sessionId}")

        resourceToSessionMap.removeIf { it.value == command.sessionId }
        val session = sessions.remove(command.sessionId) ?: return

        application.invokeLater {
            session.lifetimes.terminateCurrent()
        }
    }

    suspend fun submitCommand(command: LaunchSessionCommand) {
        commands.emit(command)
    }

    fun isResourceRunning(resourceId: String): Boolean {
        val sessionId = resourceToSessionMap[resourceId] ?: return false
        val session = sessions[sessionId] ?: return false
        return !session.lifetimes.isTerminated
    }

    suspend fun stopResource(resourceId: String) {
        val sessionId = resourceToSessionMap[resourceId] ?: return
        val session = sessions[sessionId] ?: return
        if (session.lifetimes.isTerminated) return
        withContext(Dispatchers.EDT) {
            session.lifetimes.terminateCurrent()
        }
    }

    data class Session(
        val id: String,
        val model: SessionModel,
        val lifetimes: SequentialLifetimes,
        val events: MutableSharedFlow<SessionEvent>,
        val openTelemetryProtocolServerPort: Int?
    )

    interface LaunchSessionCommand

    data class CreateSessionCommand(
        val sessionId: String,
        val sessionModel: SessionModel,
        val sessionEvents: MutableSharedFlow<SessionEvent>,
        val aspireHostConfig: AspireHostConfig,
        val sessionHostLifetimes: SequentialLifetimes
    ) : LaunchSessionCommand

    data class DeleteSessionCommand(
        val sessionId: String
    ) : LaunchSessionCommand
}