package me.rafaelldi.aspire.sessionHost

import com.intellij.database.util.common.removeIf
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.application
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rd.util.lifetime.SequentialLifetimes
import com.jetbrains.rd.util.lifetime.isNotAlive
import com.jetbrains.rider.build.BuildParameters
import com.jetbrains.rider.build.tasks.BuildTaskThrottler
import com.jetbrains.rider.model.BuildTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rafaelldi.aspire.generated.SessionModel
import me.rafaelldi.aspire.run.AspireHostConfig
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class SessionManager(private val project: Project, scope: CoroutineScope) {
    companion object {
        fun getInstance(project: Project) = project.service<SessionManager>()

        private val LOG = logger<SessionManager>()
    }

    private val sessions = mutableMapOf<String, Session>()
    private val resourceToSessionMap = mutableMapOf<String, String>()
    private val sessionsUnderRestart = ConcurrentHashMap<String, Unit>()

    private val commands = MutableSharedFlow<LaunchSessionCommand>(
        onBufferOverflow = BufferOverflow.SUSPEND,
        extraBufferCapacity = 100,
        replay = 20
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
            command.sessionLifetimeDefinition,
            SequentialLifetimes(command.sessionLifetimeDefinition),
            command.sessionEvents,
            command.aspireHostConfig.openTelemetryProtocolServerPort
        )
        sessions[command.sessionId] = session

        saveConnectionToResource(command)

        val launcher = SessionLauncher.getInstance(project)
        val processLifetime = session.processLifetimes.next()
        launcher.launchSession(
            session.id,
            session.model,
            processLifetime,
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

        LOG.trace("Connection between resource $idValue and session ${command.sessionId}")

        resourceToSessionMap[idValue] = command.sessionId
    }

    private suspend fun handleDeleteCommand(command: DeleteSessionCommand) {
        LOG.trace("Deleting session ${command.sessionId}")

        resourceToSessionMap.removeIf { it.value == command.sessionId }
        sessionsUnderRestart.remove(command.sessionId)
        val session = sessions.remove(command.sessionId) ?: return

        withContext(Dispatchers.EDT) {
            session.lifetimeDefinition.terminate()
        }

        session.events.tryEmit(SessionTerminated(command.sessionId, 0))
    }

    suspend fun submitCommand(command: LaunchSessionCommand) {
        commands.emit(command)
    }

    fun isResourceRunning(resourceId: String): Boolean {
        val sessionId = resourceToSessionMap[resourceId] ?: return false
        val session = sessions[sessionId] ?: return false
        return !session.lifetimeDefinition.isNotAlive
    }

    suspend fun restartResource(resourceId: String, withDebugger: Boolean) {
        LOG.trace("Restarting resource $resourceId")

        val sessionId = resourceToSessionMap[resourceId] ?: return
        val session = sessions[sessionId] ?: return
        if (session.lifetimeDefinition.isNotAlive) return

        val sessionUnderRestart = sessionsUnderRestart.putIfAbsent(sessionId, Unit)
        if (sessionUnderRestart != null) return

        val launcher = SessionLauncher.getInstance(project)
        val processLifetime = withContext(Dispatchers.EDT) {
            session.processLifetimes.next()
        }

        val buildParameters = BuildParameters(
            BuildTarget(),
            listOf(session.model.projectPath),
            silentMode = true
        )
        BuildTaskThrottler.getInstance(project).buildSequentially(buildParameters)

        launcher.launchSession(
            session.id,
            session.model,
            processLifetime,
            session.events,
            withDebugger,
            session.openTelemetryProtocolServerPort
        )
    }

    suspend fun stopResource(resourceId: String) {
        LOG.trace("Stopping resource $resourceId")

        val sessionId = resourceToSessionMap.remove(resourceId) ?: return
        sessionsUnderRestart.remove(sessionId)
        val session = sessions.remove(sessionId) ?: return
        if (session.lifetimeDefinition.isNotAlive) return

        LOG.trace("Stopping session $sessionId")

        withContext(Dispatchers.EDT) {
            session.lifetimeDefinition.terminate()
        }

        session.events.tryEmit(SessionTerminated(sessionId, 0))
    }

    fun sessionProcessWasTerminated(sessionId: String, exitCode: Int, text: String?) {
        LOG.trace("Stopping session $sessionId ($exitCode, $text)")

        val sessionUnderRestart = sessionsUnderRestart.remove(sessionId)
        if (sessionUnderRestart != null) return

        resourceToSessionMap.removeIf { it.value == sessionId }
        val session = sessions.remove(sessionId) ?: return
        if (session.lifetimeDefinition.isNotAlive) return

        application.invokeLater {
            session.lifetimeDefinition.terminate()
        }

        session.events.tryEmit(SessionTerminated(sessionId, exitCode))
    }

    data class Session(
        val id: String,
        val model: SessionModel,
        val lifetimeDefinition: LifetimeDefinition,
        val processLifetimes: SequentialLifetimes,
        val events: MutableSharedFlow<SessionEvent>,
        val openTelemetryProtocolServerPort: Int?
    )

    interface LaunchSessionCommand

    data class CreateSessionCommand(
        val sessionId: String,
        val sessionModel: SessionModel,
        val sessionEvents: MutableSharedFlow<SessionEvent>,
        val aspireHostConfig: AspireHostConfig,
        val sessionLifetimeDefinition: LifetimeDefinition
    ) : LaunchSessionCommand

    data class DeleteSessionCommand(
        val sessionId: String
    ) : LaunchSessionCommand
}