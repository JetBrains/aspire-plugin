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
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.forEach
import kotlin.collections.map
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

        /**
         * Time window in milliseconds to batch commands together.
         * Commands received within this window will be grouped and their projects built together.
         */
        private const val BATCH_WINDOW_MS = 1000L
    }

    private val sessionLifetimes = ConcurrentHashMap<String, LifetimeDefinition>()

    private val requests = Channel<SessionRequest>(Channel.UNLIMITED)

    init {
        scope.launch {
            while (true) {
                val batch = mutableListOf<SessionRequest>()

                // Wait for the first command
                val firstCommand = requests.receive()
                batch.add(firstCommand)

                // Collect commands within the batch window
                val batchDeadline = System.currentTimeMillis() + BATCH_WINDOW_MS
                while (true) {
                    val remainingTime = batchDeadline - System.currentTimeMillis()
                    if (remainingTime <= 0) break

                    val command = withTimeoutOrNull(remainingTime) {
                        requests.receive()
                    }

                    if (command != null) {
                        batch.add(command)
                    } else {
                        break
                    }
                }

                LOG.trace { "Processing batch of ${batch.size} command(s)" }
                handleBatchCommands(batch)
            }
        }
    }

    fun submitRequest(request: SessionRequest) {
        requests.trySend(request)
    }

    private suspend fun handleBatchCommands(batch: List<SessionRequest>) {
        val createCommands = batch.filterIsInstance<StartSessionRequest>()
        val deleteCommands = batch.filterIsInstance<StopSessionRequest>()

        if (createCommands.isNotEmpty()) {
            LOG.trace { "Received ${createCommands.size} delete command(s)" }
            val projectPaths = createCommands.map { Path(it.createSessionRequest.projectPath) }.distinct()
            buildProjects(projectPaths)
            createCommands.forEach { command ->
                handleCreateRequest(command)
            }
        }

        if (deleteCommands.isNotEmpty()) {
            LOG.trace { "Received ${deleteCommands.size} delete command(s)" }
            deleteCommands.forEach { command ->
                handleDeleteRequest(command)
            }
        }
    }

    private fun handleCreateRequest(request: StartSessionRequest) {
        LOG.info("Creating session ${request.sessionId}")
        logCreateSessionRequest(request.createSessionRequest)

        val sessionLifetimeDefinition = request.aspireHostLifetime.createNested()
        sessionLifetimes.put(sessionLifetimeDefinition.lifetime, request.sessionId, sessionLifetimeDefinition)

        sessionLifetimeDefinition.lifetime.launch {
            val processLauncher = SessionProcessLauncher.getInstance(project)
            val processLifetimeDefinition = sessionLifetimeDefinition.lifetime.createNested()

            val sessionProcessListener = createSessionProcessEventListener(
                request.sessionId,
                request.sessionEvents,
                processLifetimeDefinition
            )

            processLauncher.launchSessionProcess(
                request.sessionId,
                request.createSessionRequest,
                sessionProcessListener,
                processLifetimeDefinition.lifetime,
                request.aspireHostRunConfigName
            )
        }
    }

    private fun logCreateSessionRequest(request: CreateSessionRequest) {
        LOG.trace { "Session project path: ${request.projectPath}" }
        LOG.trace { "Session debug flag: ${request.debug}" }
        LOG.trace { "Session launch profile: ${request.launchProfile}" }
        LOG.trace { "Session disable launch profile flag: ${request.disableLaunchProfile}" }
        LOG.trace { "Session args: ${request.args?.joinToString(", ")}" }
        LOG.trace { "Session env keys: ${request.envs?.joinToString(", ") { it.key }}" }
    }

    private suspend fun handleDeleteRequest(request: StopSessionRequest) {
        LOG.info("Deleting session ${request.sessionId}")

        val sessionLifetimeDefinition = sessionLifetimes.remove(request.sessionId)
        if (sessionLifetimeDefinition == null) {
            LOG.warn("Unable to find session ${request.sessionId} lifetime")
            return
        }

        if (sessionLifetimeDefinition.isAlive) {
            withContext(Dispatchers.EDT) {
                LOG.trace { "Terminating session ${request.sessionId} lifetime" }
                sessionLifetimeDefinition.terminate()
            }
        }
    }

    private suspend fun buildProjects(projectPaths: List<Path>) {
        if (projectPaths.isEmpty()) return

        val runnableProjects = mutableListOf<Path>()
        val nonRunnableProjects = mutableListOf<Path>()

        for (projectPath in projectPaths) {
            val runnableProject = findRunnableProjectByPath(projectPath, project)
            if (runnableProject != null) {
                runnableProjects.add(projectPath)
            } else {
                nonRunnableProjects.add(projectPath)
            }
        }

        if (runnableProjects.isNotEmpty()) {
            LOG.trace { "Building ${runnableProjects.size} runnable project(s): ${runnableProjects.map { it.fileName }}"}
            val pathStrings = runnableProjects.map { it.absolutePathString() }
            val buildParameters = BuildParameters(
                BuildTarget(),
                pathStrings,
                silentMode = true
            )
            BuildTaskThrottler.getInstance(project).buildSequentially(buildParameters)
        }

        if (nonRunnableProjects.isNotEmpty()) {
            LOG.trace { "Building ${nonRunnableProjects.size} non-runnable project(s): ${nonRunnableProjects.map { it.fileName }}"}
            val buildService = DotNetBuildService.getInstance(project)
            buildService.buildProjects(nonRunnableProjects)
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

}