package me.rafaelldi.aspire.sessionHost

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
import me.rafaelldi.aspire.generated.SessionModel
import me.rafaelldi.aspire.generated.SessionUpsertResult
import me.rafaelldi.aspire.run.AspireHostProjectConfig
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class AspireSessionManager(private val project: Project, scope: CoroutineScope) {
    companion object {
        fun getInstance(project: Project) = project.service<AspireSessionManager>()

        private val LOG = logger<AspireSessionManager>()
    }

    private val sessionIds = ConcurrentHashMap<String, MutableMap<String, String>>()
    private val sessions = mutableMapOf<String, SequentialLifetimes>()

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

        aspireHostLifetime.bracketIfAlive({
            sessionIds[aspireHostConfig.debugSessionToken] = mutableMapOf()
        }, {
            sessionIds.remove(aspireHostConfig.debugSessionToken)
        })

        withContext(Dispatchers.EDT) {
            sessionHostModel.upsertSession.setSuspend { _, model ->
                upsertSession(model, sessionEvents, aspireHostConfig, aspireHostLifetime)
            }

            sessionHostModel.deleteSession.setSuspend { _, sessionId ->
                deleteSession(sessionId, aspireHostConfig)
            }
        }
    }

    private suspend fun upsertSession(
        sessionModel: SessionModel,
        sessionEvents: MutableSharedFlow<AspireSessionEvent>,
        aspireHostConfig: AspireHostProjectConfig,
        aspireHostLifetime: Lifetime
    ): SessionUpsertResult {
        val sessionIdsByHost = requireNotNull(sessionIds[aspireHostConfig.debugSessionToken])
        val newSessionId = UUID.randomUUID().toString()
        val sessionId = sessionIdsByHost.putIfAbsent(sessionModel.projectPath, newSessionId) ?: newSessionId

        val command = UpsertSessionCommand(
            sessionId,
            sessionModel,
            sessionEvents,
            aspireHostConfig,
            aspireHostLifetime
        )
        commands.emit(command)

        return SessionUpsertResult(sessionId)
    }

    private suspend fun deleteSession(sessionId: String, aspireHostConfig: AspireHostProjectConfig): Boolean {
        val sessionIdsByHost = requireNotNull(sessionIds[aspireHostConfig.debugSessionToken])
        val sessionIdPair = sessionIdsByHost.entries.firstOrNull { it.value == sessionId } ?: return false
        sessionIdsByHost.remove(sessionIdPair.key) ?: return false

        val command = DeleteSessionCommand(sessionId, aspireHostConfig.debugSessionToken)
        commands.emit(command)

        return true
    }

    private suspend fun handleCommand(command: LaunchSessionCommand) {
        when (command) {
            is UpsertSessionCommand -> handleUpsertCommand(command)
            is DeleteSessionCommand -> handleDeleteCommand(command)
        }
    }

    private suspend fun handleUpsertCommand(command: UpsertSessionCommand) {
        LOG.trace("Upserting session ${command.sessionId}")

        val previousValue = sessions[command.sessionId]
        val lifetimes =
            if (previousValue != null) {
                previousValue
            } else {
                val lifetimes = SequentialLifetimes(command.aspireHostLifetime)
                sessions[command.sessionId] = lifetimes
                lifetimes
            }

        val lifetime = lifetimes.next()

        LOG.trace("Starting new session with runner (project ${command.sessionModel})")

        val launcher = AspireSessionLauncher.getInstance(project)
        launcher.launchSession(
            command.sessionId,
            command.sessionModel,
            lifetime,
            command.sessionEvents,
            command.aspireHostConfig.isDebug,
            command.aspireHostConfig.openTelemetryProtocolServerPort
        )
    }

    private fun handleDeleteCommand(command: DeleteSessionCommand) {
        LOG.trace("Deleting session ${command.sessionId}")

        val lifetimes = sessions.remove(command.sessionId) ?: return

        application.invokeLater {
            lifetimes.terminateCurrent()
        }
    }

    interface LaunchSessionCommand

    data class UpsertSessionCommand(
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