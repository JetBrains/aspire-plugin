package com.jetbrains.rider.aspire.sessionHost

import com.intellij.execution.process.*
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.util.application
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rd.util.lifetime.isNotAlive
import com.jetbrains.rider.aspire.generated.SessionModel
import com.jetbrains.rider.aspire.run.AspireHostConfig
import com.jetbrains.rider.aspire.run.AspireHostConfiguration
import com.jetbrains.rider.build.BuildParameters
import com.jetbrains.rider.build.tasks.BuildTaskThrottler
import com.jetbrains.rider.debugger.DebuggerWorkerProcessHandler
import com.jetbrains.rider.model.BuildTarget
import com.jetbrains.rider.run.pid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class SessionManager(private val project: Project, scope: CoroutineScope) {
    companion object {
        fun getInstance(project: Project) = project.service<SessionManager>()

        private val LOG = logger<SessionManager>()
    }

    private val sessions = ConcurrentHashMap<String, Session>()

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

    suspend fun submitCommand(command: LaunchSessionCommand) {
        commands.emit(command)
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
            command.sessionEvents,
            command.aspireHostConfig.aspireHostRunConfiguration
        )
        sessions[command.sessionId] = session

        val buildParameters = BuildParameters(
            BuildTarget(),
            listOf(session.model.projectPath),
            silentMode = true
        )
        BuildTaskThrottler.getInstance(project).buildSequentially(buildParameters)

        val processLauncher = SessionProcessLauncher.getInstance(project)
        val processLifetime = session.lifetimeDefinition.lifetime

        val sessionProcessListener = createSessionProcessEventListener(session.id, session.events)

        processLauncher.launchSessionProcess(
            session.id,
            session.model,
            sessionProcessListener,
            processLifetime,
            command.aspireHostConfig.debuggingMode,
            session.hostRunConfiguration
        )
    }

    private suspend fun handleDeleteCommand(command: DeleteSessionCommand) {
        LOG.trace("Deleting session ${command.sessionId}")

        val session = sessions.remove(command.sessionId) ?: return

        withContext(Dispatchers.EDT) {
            session.lifetimeDefinition.terminate()
        }

        session.events.tryEmit(SessionTerminated(command.sessionId, 0))
    }

    private fun createSessionProcessEventListener(
        sessionId: String,
        sessionEvents: MutableSharedFlow<SessionEvent>
    ): ProcessListener =
        object : ProcessAdapter() {
            override fun startNotified(event: ProcessEvent) {
                LOG.trace { "Aspire session process was started (id: $sessionId)" }
                val processHandler = event.processHandler
                val pid = when (processHandler) {
                    is DebuggerWorkerProcessHandler -> processHandler.debuggerWorkerRealHandler.pid()
                    is ProcessHandler -> event.processHandler.pid()
                    else -> null
                }
                if (pid == null) {
                    LOG.warn("Unable to determine process id for the session $sessionId")
                    sessionEvents.tryEmit(SessionTerminated(sessionId, -1))
                } else {
                    sessionEvents.tryEmit(SessionStarted(sessionId, pid))
                }
            }

            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                val text =
                    if (event.text.endsWith("\r\n")) event.text.substring(0, event.text.length - 2)
                    else if (event.text.endsWith("\n")) event.text.substring(0, event.text.length - 1)
                    else event.text
                val isStdErr = outputType == ProcessOutputType.STDERR
                sessionEvents.tryEmit(SessionLogReceived(sessionId, isStdErr, text))
            }

            override fun processNotStarted() {
                LOG.warn("Aspire session process is not started")
                sessionEvents.tryEmit(SessionTerminated(sessionId, -1))
            }

            override fun processTerminated(event: ProcessEvent) {
                LOG.trace("Stopping session $sessionId (${event.exitCode}, ${event.text})")

                val session = sessions.remove(sessionId) ?: return
                if (session.lifetimeDefinition.isNotAlive) return

                application.invokeLater {
                    session.lifetimeDefinition.terminate()
                }

                session.events.tryEmit(SessionTerminated(sessionId, event.exitCode))
            }
        }

    data class Session(
        val id: String,
        val model: SessionModel,
        val lifetimeDefinition: LifetimeDefinition,
        val events: MutableSharedFlow<SessionEvent>,
        val hostRunConfiguration: AspireHostConfiguration?
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