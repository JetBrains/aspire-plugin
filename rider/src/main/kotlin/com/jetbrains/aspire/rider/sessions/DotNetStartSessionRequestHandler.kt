@file:Suppress("LoggingSimilarMessage")

package com.jetbrains.aspire.rider.sessions

import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputType
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.util.application
import com.jetbrains.aspire.sessions.DotNetSessionLaunchConfiguration
import com.jetbrains.aspire.sessions.SessionEvent
import com.jetbrains.aspire.sessions.SessionLaunchMode
import com.jetbrains.aspire.sessions.SessionLaunchPreferenceService
import com.jetbrains.aspire.sessions.SessionLogReceived
import com.jetbrains.aspire.sessions.SessionStarted
import com.jetbrains.aspire.sessions.SessionTerminated
import com.jetbrains.aspire.sessions.StartSessionRequest
import com.jetbrains.aspire.sessions.StartSessionRequestHandler
import com.jetbrains.aspire.sessions.findRunnableProjectByPath
import com.jetbrains.aspire.util.DotNetBuildService
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rd.util.lifetime.isAlive
import com.jetbrains.rd.util.lifetime.isNotAlive
import com.jetbrains.rd.util.threading.coroutines.launch
import com.jetbrains.rider.build.BuildParameters
import com.jetbrains.rider.build.tasks.BuildTaskThrottler
import com.jetbrains.rider.debugger.DebuggerWorkerProcessHandler
import com.jetbrains.rider.model.BuildTarget
import com.jetbrains.rider.run.pid
import kotlinx.coroutines.channels.Channel
import java.nio.file.Path
import kotlin.io.path.absolutePathString

internal class DotNetStartSessionRequestHandler : StartSessionRequestHandler {
    companion object {
        private val LOG = logger<DotNetStartSessionRequestHandler>()
    }

    override fun isApplicable(request: StartSessionRequest) =
        request.launchConfiguration is DotNetSessionLaunchConfiguration

    override suspend fun handleRequests(requests: List<StartSessionRequest>, project: Project) {
        LOG.trace { "Received ${requests.size} start session request(s)" }

        val projectPaths = requests.map { it.launchConfiguration.projectPath }.distinct()
        buildProjects(projectPaths, project)

        requests.forEach {
            handleStartSessionRequest(it, project)
        }
    }

    private suspend fun buildProjects(projectPaths: List<Path>, project: Project) {
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
            LOG.trace { "Building ${runnableProjects.size} runnable project(s): ${runnableProjects.map { it.fileName }}" }
            val pathStrings = runnableProjects.map { it.absolutePathString() }
            val buildParameters = BuildParameters(
                BuildTarget(),
                pathStrings,
                silentMode = true
            )
            BuildTaskThrottler.getInstance(project).buildSequentially(buildParameters)
        }

        if (nonRunnableProjects.isNotEmpty()) {
            LOG.trace { "Building ${nonRunnableProjects.size} non-runnable project(s): ${nonRunnableProjects.map { it.fileName }}" }
            val buildService = DotNetBuildService.Companion.getInstance(project)
            buildService.buildProjects(nonRunnableProjects)
        }
    }

    private fun handleStartSessionRequest(request: StartSessionRequest, project: Project) {
        LOG.info("Creating session ${request.sessionId}")

        logLaunchConfiguration(request.launchConfiguration)

        val sessionLifetime = request.sessionLifetime.lifetime
        sessionLifetime.launch {
            val sessionProcessListener = createSessionProcessEventListener(
                request.sessionId,
                request.sessionEvents,
                request.sessionLifetime
            )

            launchSessionProcess(
                request.sessionId,
                request.launchConfiguration,
                sessionProcessListener,
                sessionLifetime,
                request.aspireHostRunConfigName,
                project
            )
        }
    }

    private fun logLaunchConfiguration(launchConfiguration: DotNetSessionLaunchConfiguration) {
        LOG.trace { "Session project path: ${launchConfiguration.projectPath}" }
        LOG.trace { "Session debug flag: ${launchConfiguration.debug}" }
        LOG.trace { "Session launch profile: ${launchConfiguration.launchProfile}" }
        LOG.trace { "Session disable launch profile flag: ${launchConfiguration.disableLaunchProfile}" }
        LOG.trace { "Session args: ${launchConfiguration.args?.joinToString(", ")}" }
        LOG.trace { "Session env keys: ${launchConfiguration.envs?.joinToString(", ") { it.first }}" }
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

    private suspend fun launchSessionProcess(
        sessionId: String,
        launchConfiguration: DotNetSessionLaunchConfiguration,
        sessionProcessEventListener: ProcessListener,
        sessionProcessLifetime: Lifetime,
        aspireHostRunConfigName: String?,
        project: Project,
    ) {
        LOG.info("Starting a session process for the project ${launchConfiguration.projectPath}")

        if (sessionProcessLifetime.isNotAlive) {
            LOG.warn("Unable to run project ${launchConfiguration.projectPath} because lifetimes are not alive")
            return
        }

        val preferred = SessionLaunchPreferenceService
            .getInstance(project)
            .getPreferredLaunchMode(launchConfiguration.projectPath.absolutePathString())
        val shouldDebug = when (preferred) {
            SessionLaunchMode.DEBUG -> true
            SessionLaunchMode.RUN -> false
            null -> launchConfiguration.debug
        }

        if (shouldDebug) {
            launchDebugProcess(
                sessionId,
                launchConfiguration,
                sessionProcessEventListener,
                sessionProcessLifetime,
                aspireHostRunConfigName,
                project,
            )
        } else {
            launchRunProcess(
                sessionId,
                launchConfiguration,
                sessionProcessEventListener,
                sessionProcessLifetime,
                aspireHostRunConfigName,
                project,
            )
        }
    }

    private suspend fun launchDebugProcess(
        sessionId: String,
        launchConfiguration: DotNetSessionLaunchConfiguration,
        sessionProcessEventListener: ProcessListener,
        sessionProcessLifetime: Lifetime,
        aspireHostRunConfigName: String?,
        project: Project
    ) {
        val processLauncher = getSessionProcessLauncher(launchConfiguration.projectPath, project)
        if (processLauncher == null) {
            LOG.warn("Unable to find appropriate process launcher for the project ${launchConfiguration.projectPath}")
            return
        }

        processLauncher.launchDebugProcess(
            sessionId,
            launchConfiguration,
            sessionProcessEventListener,
            sessionProcessLifetime,
            aspireHostRunConfigName,
            project,
        )
    }

    private suspend fun launchRunProcess(
        sessionId: String,
        launchConfiguration: DotNetSessionLaunchConfiguration,
        sessionProcessEventListener: ProcessListener,
        sessionProcessLifetime: Lifetime,
        aspireHostRunConfigName: String?,
        project: Project
    ) {
        val processLauncher = getSessionProcessLauncher(launchConfiguration.projectPath, project)
        if (processLauncher == null) {
            LOG.warn("Unable to find appropriate process launcher for the project ${launchConfiguration.projectPath}")
            return
        }

        processLauncher.launchRunProcess(
            sessionId,
            launchConfiguration,
            sessionProcessEventListener,
            sessionProcessLifetime,
            aspireHostRunConfigName,
            project,
        )
    }

    private suspend fun getSessionProcessLauncher(
        projectPath: Path,
        project: Project
    ): DotNetSessionProcessLauncherExtension? {
        val pathString = projectPath.absolutePathString()
        for (launcher in DotNetSessionProcessLauncherExtension.Companion.EP_NAME.extensionList.sortedBy { it.priority }) {
            if (launcher.isApplicable(pathString, project))
                return launcher
        }

        return null
    }
}