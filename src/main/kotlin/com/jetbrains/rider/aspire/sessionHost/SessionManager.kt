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
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rd.util.lifetime.isAlive
import com.jetbrains.rd.util.put
import com.jetbrains.rider.aspire.generated.CreateSessionRequest
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

    private val sessionLifetimes = ConcurrentHashMap<String, LifetimeDefinition>()

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
        LOG.info("Creating session ${command.sessionId}")
        LOG.trace { "Session details ${command.createSessionRequest}" }

        val sessionLifetimeDefinition = command.aspireHostLifetime.createNested()
        sessionLifetimes.put(sessionLifetimeDefinition.lifetime, command.sessionId, sessionLifetimeDefinition)

        val buildParameters = BuildParameters(
            BuildTarget(),
            listOf(command.createSessionRequest.projectPath),
            silentMode = true
        )
        BuildTaskThrottler.getInstance(project).buildSequentially(buildParameters)

        val processLauncher = SessionProcessLauncher.getInstance(project)
        val processLifetimeDefinition = sessionLifetimeDefinition.lifetime.createNested()

        val sessionProcessListener = createSessionProcessEventListener(
            command.sessionId,
            command.sessionEvents,
            processLifetimeDefinition
        )

        processLauncher.launchSessionProcess(
            command.sessionId,
            command.createSessionRequest,
            sessionProcessListener,
            processLifetimeDefinition.lifetime,
            command.isAspireHostUnderDebug,
            command.aspireHostRunConfigName
        )
    }

    private suspend fun handleDeleteCommand(command: DeleteSessionCommand) {
        LOG.info("Deleting session ${command.sessionId}")

        val sessionLifetimeDefinition = sessionLifetimes.remove(command.sessionId)
        if (sessionLifetimeDefinition == null) {
            LOG.warn("Unable to find session ${command.sessionId} lifetime")
            return
        }

        if (sessionLifetimeDefinition.isAlive) {
            withContext(Dispatchers.EDT) {
                LOG.trace { "Terminating session ${command.sessionId} lifetime" }
                sessionLifetimeDefinition.terminate()
            }
        }
    }

    private fun createSessionProcessEventListener(
        sessionId: String,
        sessionEvents: MutableSharedFlow<SessionEvent>,
        processLifetimeDefinition: LifetimeDefinition
    ): ProcessListener =
        object : ProcessAdapter() {
            override fun startNotified(event: ProcessEvent) {
                LOG.info("Session $sessionId process was started")
                val processHandler = event.processHandler
                val pid = when (processHandler) {
                    is DebuggerWorkerProcessHandler -> processHandler.debuggerWorkerRealHandler.pid()
                    is ProcessHandler -> event.processHandler.pid()
                    else -> null
                }
                if (pid == null) {
                    LOG.warn("Unable to determine process id for the session $sessionId")
                    terminateSession(-1)
                } else {
                    LOG.trace { "Session $sessionId process id = $pid" }
                    val eventSendingResult = sessionEvents.tryEmit(SessionStarted(sessionId, pid))
                    if (!eventSendingResult) {
                        LOG.warn("Unable to send an event for session $sessionId start")
                    }
                }
            }

            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                val text =
                    if (event.text.endsWith("\r\n")) event.text.substring(0, event.text.length - 2)
                    else if (event.text.endsWith("\n")) event.text.substring(0, event.text.length - 1)
                    else event.text
                val isStdErr = outputType == ProcessOutputType.STDERR
                val eventSendingResult = sessionEvents.tryEmit(SessionLogReceived(sessionId, isStdErr, text))
                if (!eventSendingResult) {
                    LOG.warn("Unable to send an event for session $sessionId log")
                }
            }

            override fun processNotStarted() {
                LOG.warn("Session $sessionId process is not started")
                terminateSession(-1)
            }

            override fun processTerminated(event: ProcessEvent) {
                LOG.info("Session $sessionId process was terminated (${event.exitCode}, ${event.text})")
                terminateSession(event.exitCode)
            }

            private fun terminateSession(exitCode: Int) {
                LOG.trace { "Terminating session $sessionId with exitCode $exitCode" }
                val eventSendingResult = sessionEvents.tryEmit(SessionTerminated(sessionId, exitCode))
                if (!eventSendingResult) {
                    LOG.warn("Unable to send an event for session $sessionId termination")
                }
                if (processLifetimeDefinition.isAlive) {
                    application.invokeLater {
                        LOG.trace { "Terminating session $sessionId lifetime" }
                        processLifetimeDefinition.terminate()
                    }
                }
            }
        }

    interface LaunchSessionCommand

    data class CreateSessionCommand(
        val sessionId: String,
        val createSessionRequest: CreateSessionRequest,
        val sessionEvents: MutableSharedFlow<SessionEvent>,
        val isAspireHostUnderDebug: Boolean,
        val aspireHostRunConfigName: String?,
        val aspireHostLifetime: Lifetime
    ) : LaunchSessionCommand

    data class DeleteSessionCommand(
        val sessionId: String
    ) : LaunchSessionCommand
}