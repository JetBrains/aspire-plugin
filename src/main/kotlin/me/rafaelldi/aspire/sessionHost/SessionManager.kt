package me.rafaelldi.aspire.sessionHost

import com.intellij.database.util.common.removeIf
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.application
import com.jetbrains.rd.framework.util.setSuspend
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.SequentialLifetimes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rafaelldi.aspire.generated.AspireSessionHostModel
import me.rafaelldi.aspire.generated.SessionCreationResult
import me.rafaelldi.aspire.generated.SessionModel
import me.rafaelldi.aspire.run.AspireHostConfig
import java.util.*

@Service(Service.Level.PROJECT)
class SessionManager(private val project: Project, scope: CoroutineScope) {
    companion object {
        fun getInstance(project: Project) = project.service<SessionManager>()

        private val LOG = logger<SessionManager>()
    }

    private val sessions = mutableMapOf<String, Session>()
    private val resourceToSessionMap = mutableMapOf<String, String>()

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
        aspireHostConfig: AspireHostConfig,
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
        aspireHostConfig: AspireHostConfig,
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

    fun isResourceRunning(resourceId: String): Boolean {
        val sessionId = resourceToSessionMap[resourceId] ?: return false
        val session = sessions[sessionId] ?: return false
        return !session.lifetimes.isTerminated
    }

    fun isResourceStopped(resourceId: String): Boolean {
        val sessionId = resourceToSessionMap[resourceId] ?: return false
        val session = sessions[sessionId] ?: return false
        return session.lifetimes.isTerminated
    }

    suspend fun startResource(resourceId: String) {
        val sessionId = resourceToSessionMap[resourceId] ?: return
        val session = sessions[sessionId] ?: return
        if (!session.lifetimes.isTerminated) return
        launchSession(session, false)
    }

    suspend fun debugResource(resourceId: String) {
        val sessionId = resourceToSessionMap[resourceId] ?: return
        val session = sessions[sessionId] ?: return
        if (!session.lifetimes.isTerminated) return
        launchSession(session, true)
    }

    private suspend fun launchSession(session: Session, debuggingMode: Boolean) {
        val launcher = SessionLauncher.getInstance(project)
        launcher.launchSession(
            session.id,
            session.model,
            session.lifetimes.next(),
            session.events,
            debuggingMode,
            session.openTelemetryProtocolServerPort
        )
    }

    fun stopResource(resourceId: String) {
        val sessionId = resourceToSessionMap[resourceId] ?: return
        val session = sessions[sessionId] ?: return
        if (session.lifetimes.isTerminated) return
        session.lifetimes.terminateCurrent()
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
            SequentialLifetimes(command.sessionHostLifetime),
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
        val sessionHostLifetime: Lifetime
    ) : LaunchSessionCommand

    data class DeleteSessionCommand(
        val sessionId: String
    ) : LaunchSessionCommand
}