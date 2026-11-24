@file:Suppress("LoggingSimilarMessage", "UnstableApiUsage")

package com.jetbrains.aspire.rider.sessions.sessionHandlers

import com.intellij.execution.ExecutionException
import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.serialization.impl.toPath
import com.intellij.util.application
import com.jetbrains.aspire.AspireService
import com.jetbrains.aspire.extensions.StartSessionRequestHandler
import com.jetbrains.aspire.rider.generated.GetReferencedProjectsFromAppHostRequest
import com.jetbrains.aspire.rider.generated.aspirePluginModel
import com.jetbrains.aspire.rider.processLaunchers.DotNetSessionProcessLauncherExtension
import com.jetbrains.aspire.rider.processLaunchers.SessionProcessLaunchConfiguration
import com.jetbrains.aspire.rider.sessions.SessionProcessEventListener
import com.jetbrains.aspire.rider.util.DotNetBuildService
import com.jetbrains.aspire.rider.util.findExistingAppHost
import com.jetbrains.aspire.sessions.MessageLevel
import com.jetbrains.aspire.sessions.SessionEvent
import com.jetbrains.aspire.sessions.SessionLaunchMode
import com.jetbrains.aspire.sessions.SessionLaunchPreferenceService
import com.jetbrains.aspire.sessions.SessionMessageReceived
import com.jetbrains.aspire.sessions.StartSessionRequest
import com.jetbrains.aspire.settings.AspireSettings
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rd.util.lifetime.isAlive
import com.jetbrains.rd.util.lifetime.isNotAlive
import com.jetbrains.rd.util.threading.coroutines.launch
import com.jetbrains.rider.build.BuildParameters
import com.jetbrains.rider.build.tasks.BuildTaskThrottler
import com.jetbrains.rider.ijent.extensions.toNioPath
import com.jetbrains.rider.ijent.extensions.toRd
import com.jetbrains.rider.model.BuildTarget
import com.jetbrains.rider.model.SilentMode
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.projectView.workspace.findProjects
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.absolutePathString

/**
 *  Base for handlers of Aspire session requests that start .NET projects.
 *
 *  The handler builds all the requested projects (unless [buildProjectsBeforeLaunch] is `false`) and then launches
 *  each of them with the [DotNetSessionProcessLauncherExtension] returned by [findLauncher].
 */
internal abstract class AbstractDotNetStartSessionRequestHandler : StartSessionRequestHandler {
    companion object {
        private val LOG = logger<AbstractDotNetStartSessionRequestHandler>()
    }

    /**
     * Whether the requested projects are built before they are launched.
     */
    protected open val buildProjectsBeforeLaunch = true

    /**
     * Converts the launch configuration of the [request] to a [SessionProcessLaunchConfiguration],
     * or returns `null` if the request isn't supported by this handler.
     */
    protected abstract fun toProcessLaunchConfiguration(request: StartSessionRequest): SessionProcessLaunchConfiguration?

    /**
     * Finds a process launcher for the project, or returns `null` if there is no suitable launcher.
     */
    protected abstract suspend fun findLauncher(
        projectPath: Path,
        project: Project
    ): DotNetSessionProcessLauncherExtension?

    override suspend fun handleRequests(requests: List<StartSessionRequest>, project: Project) {
        LOG.trace { "Received ${requests.size} start session request(s)" }

        val launchConfigurations = requests.mapNotNull { request ->
            val launchConfiguration = toProcessLaunchConfiguration(request)
            if (launchConfiguration == null) {
                LOG.warn("Unsupported launch configuration: ${request.launchConfiguration::class.simpleName}")
                return@mapNotNull null
            }
            request to launchConfiguration
        }

        if (buildProjectsBeforeLaunch) {
            val projectPaths = launchConfigurations
                .map { it.second.projectPath }
                .distinct()
            buildProjects(projectPaths, project)
        }

        launchConfigurations.forEach { (request, launchConfiguration) ->
            handleStartSessionRequest(request, launchConfiguration, project)
        }
    }

    @Suppress("JetBrainsInternalApiUsage")
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
                silentMode = SilentMode.Silent
            )
            BuildTaskThrottler.getInstance(project).buildSequentially(buildParameters)
        }
    }

    private suspend fun buildExternalProjects(projectPaths: List<Path>, project: Project) {
        if (projectPaths.isEmpty()) return

        val settings = AspireSettings.getInstance()
        if (!settings.buildExternalNetProjects) {
            LOG.trace("Skip building external .NET projects")
            return
        }

        LOG.trace { "Building ${projectPaths.size} external project(s): ${projectPaths.map { it.fileName }}" }
        val buildService = DotNetBuildService.getInstance(project)
        buildService.buildProjects(projectPaths)
    }

    @Suppress("JetBrainsInternalApiUsage")
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

    private fun handleStartSessionRequest(
        request: StartSessionRequest,
        launchConfiguration: SessionProcessLaunchConfiguration,
        project: Project
    ) {
        LOG.info("Creating session ${request.sessionId}")

        logLaunchConfiguration(launchConfiguration, request.launchConfiguration.debug)

        val sessionLifetime = request.sessionLifetime.lifetime
        sessionLifetime.launch {
            val sessionProcessListener = createSessionProcessEventListener(
                request.sessionId,
                request.sessionEvents,
                request.sessionLifetime,
                project
            )

            try {
                launchSessionProcess(request, launchConfiguration, sessionProcessListener, project)
            } catch (e: ExecutionException) {
                LOG.warn("Unable to launch session ${request.sessionId}", e)
                val message = e.message ?: "Unable to launch ${launchConfiguration.projectPath}"
                request.sessionEvents.trySend(SessionMessageReceived(request.sessionId, MessageLevel.Error, message, null))
                sessionProcessListener.processNotStarted()
            }
        }
    }

    private fun logLaunchConfiguration(launchConfiguration: SessionProcessLaunchConfiguration, debug: Boolean) {
        LOG.trace { "Session project path: ${launchConfiguration.projectPath}" }
        LOG.trace { "Session debug flag: $debug" }
        LOG.trace { "Session launch profile: ${launchConfiguration.launchProfile}" }
        LOG.trace { "Session disable launch profile flag: ${launchConfiguration.disableLaunchProfile}" }
        LOG.trace { "Session args: ${launchConfiguration.args?.joinToString(", ")}" }
        LOG.trace { "Session env keys: ${launchConfiguration.envs?.joinToString(", ") { it.first }}" }
    }

    private fun createSessionProcessEventListener(
        sessionId: String,
        sessionEvents: Channel<SessionEvent>,
        processLifetimeDefinition: LifetimeDefinition,
        project: Project
    ): ProcessListener {
        return SessionProcessEventListener(
            sessionId,
            sessionEvents,
            AspireService.getInstance(project).lifetime
        ) {
            if (processLifetimeDefinition.isAlive) {
                application.invokeLater {
                    LOG.trace { "Terminating session $sessionId lifetime" }
                    processLifetimeDefinition.terminate()
                }
            }
        }
    }

    private suspend fun launchSessionProcess(
        request: StartSessionRequest,
        launchConfiguration: SessionProcessLaunchConfiguration,
        sessionProcessEventListener: ProcessListener,
        project: Project,
    ) {
        LOG.info("Starting a session process for the project ${launchConfiguration.projectPath}")

        val sessionProcessLifetime = request.sessionLifetime.lifetime
        if (sessionProcessLifetime.isNotAlive) {
            LOG.warn("Unable to run project ${launchConfiguration.projectPath} because lifetimes are not alive")
            return
        }

        val processLauncher = findLauncher(launchConfiguration.projectPath, project)
        if (processLauncher == null) {
            LOG.warn("Unable to find appropriate process launcher for the project ${launchConfiguration.projectPath}")
            sessionProcessEventListener.processNotStarted()
            return
        }

        val preferred = SessionLaunchPreferenceService
            .getInstance(project)
            .getPreferredLaunchMode(launchConfiguration.projectPath.absolutePathString())
        val shouldDebug = when (preferred) {
            SessionLaunchMode.DEBUG -> true
            SessionLaunchMode.RUN -> false
            null -> request.launchConfiguration.debug
        }

        if (shouldDebug) {
            processLauncher.launchDebugProcess(
                request.sessionId,
                launchConfiguration,
                sessionProcessEventListener,
                sessionProcessLifetime,
                request.appHostFile,
                project,
            )
        } else {
            processLauncher.launchRunProcess(
                request.sessionId,
                launchConfiguration,
                sessionProcessEventListener,
                sessionProcessLifetime,
                request.appHostFile,
                project,
            )
        }
    }
}
