@file:Suppress("LoggingSimilarMessage", "UnstableApiUsage")

package com.jetbrains.aspire.rider.sessions

import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.serialization.impl.toPath
import com.intellij.util.application
import com.jetbrains.aspire.rider.generated.GetReferencedProjectsFromAppHostRequest
import com.jetbrains.aspire.rider.generated.aspirePluginModel
import com.jetbrains.aspire.rider.util.DotNetBuildService
import com.jetbrains.aspire.rider.util.findExistingAppHost
import com.jetbrains.aspire.sessions.*
import com.jetbrains.aspire.settings.AspireSettings
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rd.util.lifetime.isAlive
import com.jetbrains.rd.util.lifetime.isNotAlive
import com.jetbrains.rd.util.threading.coroutines.launch
import com.jetbrains.rider.build.BuildParameters
import com.jetbrains.rider.build.tasks.BuildTaskThrottler
import com.jetbrains.rider.debugger.DebuggerWorkerProcessHandler
import com.jetbrains.rider.ijent.extensions.toNioPath
import com.jetbrains.rider.ijent.extensions.toRd
import com.jetbrains.rider.model.BuildTarget
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.projectView.workspace.findProjects
import com.jetbrains.rider.run.pid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.absolutePathString

/**
 *  Starting point for handling all dotnet-related Aspire session requests.
 *
 *  The handler could build all the requested projects, detect the type of each project, and then launch them
 *  by using a specific [DotNetSessionProcessLauncherExtension].
 */
internal class DotNetStartSessionRequestHandler : StartSessionRequestHandler {
    companion object {
        private val LOG = logger<DotNetStartSessionRequestHandler>()
    }

    override fun isApplicable(request: StartSessionRequest) =
        request.launchConfiguration is DotNetSessionLaunchConfiguration

    override suspend fun handleRequests(requests: List<StartSessionRequest>, project: Project) {
        LOG.trace { "Received ${requests.size} start session request(s)" }

        val projectPaths = requests
            .asSequence()
            .map { it.launchConfiguration }
            .filterIsInstance<DotNetSessionLaunchConfiguration>()
            .map { it.projectPath }
            .distinct()
            .toList()
        buildProjects(projectPaths, project)

        requests.forEach {
            handleStartSessionRequest(it, project)
        }
    }

    private suspend fun buildProjects(projectPaths: List<Path>, project: Project) {
        if (projectPaths.isEmpty()) return

        val allSolutionProjectPaths = project.serviceAsync<WorkspaceModel>()
            .findProjects()
            .mapNotNull { it.url?.toPath() }
            .toSet()

        val solutionProjects = mutableListOf<Path>()
        val externalProjects = mutableListOf<Path>()

        for (projectPath in projectPaths) {
            if (allSolutionProjectPaths.contains(projectPath)) {
                solutionProjects.add(projectPath)
            } else {
                externalProjects.add(projectPath)
            }
        }

        coroutineScope {
            launch {
                buildSolutionProjects(solutionProjects, project)
            }
            launch {
                buildExternalProjects(externalProjects, project)
            }
        }
    }

    private suspend fun buildSolutionProjects(projectPaths: List<Path>, project: Project) {
        if (projectPaths.isEmpty()) return

        val settings = AspireSettings.getInstance()

        val projectPathsToBuild = if (settings.forceBuildOfAppHostReferencedProjects) {
            projectPaths
        } else {
            //We can skip building referenced by AppHost projects
            // because they are already built with BeforeLaunchTask
            val referencedProjects = findProjectsReferencedByAppHost(projectPaths, project)
            projectPaths.filterNot { projectPath ->
                referencedProjects.any { it == projectPath }
            }
        }

        if (projectPathsToBuild.isNotEmpty()) {
            LOG.trace { "Building ${projectPathsToBuild.size} solution project(s): ${projectPathsToBuild.map { it.fileName }}" }

            val pathStrings = projectPathsToBuild.map { it.absolutePathString() }
            val buildParameters = BuildParameters(
                BuildTarget(),
                pathStrings,
                silentMode = true
            )
            BuildTaskThrottler.getInstance(project).buildSequentially(buildParameters)
        }
    }

    private suspend fun buildExternalProjects(projectPaths: List<Path>, project: Project) {
        if (projectPaths.isEmpty()) return
        LOG.trace { "Building ${projectPaths.size} external project(s): ${projectPaths.map { it.fileName }}" }
        val buildService = DotNetBuildService.getInstance(project)
        buildService.buildProjects(projectPaths)
    }

    private suspend fun findProjectsReferencedByAppHost(projectPaths: List<Path>, project: Project): List<Path> {
        val appHostProjectPath = findExistingAppHost(project)
            ?.url
            ?.toPath()
            ?: return emptyList()

        val request = GetReferencedProjectsFromAppHostRequest(
            appHostProjectPath.toRd(),
            projectPaths.map { it.toRd() }
        )

        val referencedProjectsResponse = withContext(Dispatchers.EDT) {
            project.solution.aspirePluginModel.getReferencedProjectsFromAppHost.startSuspending(request)
        } ?: return emptyList()

        return referencedProjectsResponse.referencedProjectFilePaths.map { it.toNioPath() }
    }

    private fun handleStartSessionRequest(request: StartSessionRequest, project: Project) {
        LOG.info("Creating session ${request.sessionId}")

        val launchConfiguration = request.launchConfiguration as? DotNetSessionLaunchConfiguration
        if (launchConfiguration == null) {
            LOG.warn("Launch configuration is not a DotNetSessionLaunchConfiguration: ${request.launchConfiguration::class.simpleName}")
            return
        }

        logLaunchConfiguration(launchConfiguration)

        val sessionLifetime = request.sessionLifetime.lifetime
        sessionLifetime.launch {
            val sessionProcessListener = createSessionProcessEventListener(
                request.sessionId,
                request.sessionEvents,
                request.sessionLifetime
            )

            launchSessionProcess(
                request.sessionId,
                launchConfiguration,
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
                    val eventSendingResult = sessionEvents.trySend(SessionProcessStarted(sessionId, pid))
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
                val eventSendingResult = sessionEvents.trySend(SessionProcessTerminated(sessionId, exitCode))
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
        val processLauncher =
            DotNetSessionProcessLauncherExtension.getApplicableLauncher(launchConfiguration.projectPath, project)
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
        val processLauncher =
            DotNetSessionProcessLauncherExtension.getApplicableLauncher(launchConfiguration.projectPath, project)
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
}