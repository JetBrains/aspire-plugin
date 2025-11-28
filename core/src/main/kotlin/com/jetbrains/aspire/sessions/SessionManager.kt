package com.jetbrains.aspire.sessions

import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputType
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
import com.jetbrains.rd.util.threading.coroutines.launch
import com.jetbrains.aspire.generated.CreateSessionRequest
import com.jetbrains.aspire.util.DotNetBuildService
import com.jetbrains.rider.build.BuildParameters
import com.jetbrains.rider.build.tasks.BuildTaskThrottler
import com.jetbrains.rider.debugger.DebuggerWorkerProcessHandler
import com.jetbrains.rider.model.BuildTarget
import com.jetbrains.rider.run.pid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

/**
 * Service for managing the lifecycle and orchestration of Aspire sessions.
 *
 * This class is responsible for handling requests to create and delete sessions, as well as
 * managing their associated lifetimes and processes. It processes commands submitted to it
 * asynchronously via a command channel.
 */
@Service(Service.Level.PROJECT)
internal class SessionManager(private val project: Project, scope: CoroutineScope) {
    companion object {
        fun getInstance(project: Project) = project.service<SessionManager>()

        private val LOG = logger<SessionManager>()
    }

    private val sessionLifetimes = ConcurrentHashMap<String, LifetimeDefinition>()

    private val commands = Channel<LaunchSessionCommand>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (command in commands) {
                handleCommand(command)
            }
        }
    }

    fun submitCommand(command: LaunchSessionCommand) {
        commands.trySend(command)
    }

    private suspend fun handleCommand(command: LaunchSessionCommand) {
        when (command) {
            is CreateSessionCommand -> handleCreateCommand(command)
            is DeleteSessionCommand -> handleDeleteCommand(command)
        }
    }

    private fun handleCreateCommand(command: CreateSessionCommand) {
        LOG.info("Creating session ${command.sessionId}")
        logCreateSessionRequest(command.createSessionRequest)

        val sessionLifetimeDefinition = command.aspireHostLifetime.createNested()
        sessionLifetimes.put(sessionLifetimeDefinition.lifetime, command.sessionId, sessionLifetimeDefinition)

        sessionLifetimeDefinition.lifetime.launch {
            buildProject(Path(command.createSessionRequest.projectPath))

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
                command.aspireHostRunConfigName
            )
        }
    }

    private fun logCreateSessionRequest(createSessionRequest: CreateSessionRequest) {
        LOG.trace { "Session project path: ${createSessionRequest.projectPath}" }
        LOG.trace { "Session debug flag: ${createSessionRequest.debug}" }
        LOG.trace { "Session launch profile: ${createSessionRequest.launchProfile}" }
        LOG.trace { "Session disable launch profile flag: ${createSessionRequest.disableLaunchProfile}" }
        LOG.trace { "Session args: ${createSessionRequest.args?.joinToString(", ")}" }
        LOG.trace { "Session env keys: ${createSessionRequest.envs?.joinToString(", ") { it.key }}" }
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

    private suspend fun buildProject(projectPath: Path) {
        val runnableProject = findRunnableProjectByPath(projectPath, project)
        if (runnableProject != null) {
            val buildParameters = BuildParameters(
                BuildTarget(),
                listOf(projectPath.absolutePathString()),
                silentMode = true
            )
            BuildTaskThrottler.getInstance(project).buildSequentially(buildParameters)
        } else {
            val buildService = DotNetBuildService.getInstance(project)
            buildService.buildProject(projectPath)
        }
    }

    private fun createSessionProcessEventListener(
        sessionId: String,
        sessionEvents: Channel<SessionEvent>,
        processLifetimeDefinition: LifetimeDefinition
    ): ProcessListener =
        object : ProcessListener {
            override fun startNotified(event: ProcessEvent) {
                LOG.info("Session $sessionId process was started")
                val pid = when (val processHandler = event.processHandler) {
                    is DebuggerWorkerProcessHandler -> processHandler.debuggerWorkerRealHandler.pid()
                    is ProcessHandler -> event.processHandler.pid()
                    else -> null
                }
                if (pid == null) {
                    LOG.warn("Unable to determine process id for the session $sessionId")
                    terminateSession(-1)
                } else {
                    LOG.trace { "Session $sessionId process id = $pid" }
                    val eventSendingResult = sessionEvents.trySend(SessionStarted(sessionId, pid))
                    if (!eventSendingResult.isSuccess) {
                        LOG.warn("Unable to send an event for session $sessionId start")
                    }
                }
            }

            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                val isStdErr = outputType == ProcessOutputType.STDERR
                val eventSendingResult = sessionEvents.trySend(SessionLogReceived(sessionId, isStdErr, event.text))
                if (!eventSendingResult.isSuccess) {
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
                val eventSendingResult = sessionEvents.trySend(SessionTerminated(sessionId, exitCode))
                if (!eventSendingResult.isSuccess) {
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
        val sessionEvents: Channel<SessionEvent>,
        val aspireHostRunConfigName: String?,
        val aspireHostLifetime: Lifetime
    ) : LaunchSessionCommand

    data class DeleteSessionCommand(
        val sessionId: String
    ) : LaunchSessionCommand
}