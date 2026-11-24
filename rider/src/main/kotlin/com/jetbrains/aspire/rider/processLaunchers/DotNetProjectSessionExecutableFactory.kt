package com.jetbrains.aspire.rider.processLaunchers

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.util.io.systemIndependentPath
import com.jetbrains.aspire.rider.launchProfiles.getWorkingDirectory
import com.jetbrains.aspire.rider.run.AspireRiderRunConfiguration
import com.jetbrains.aspire.rider.util.MSBuildPropertyService
import com.jetbrains.aspire.rider.util.ProjectRunProperties
import com.jetbrains.aspire.rider.util.findByProjectPath
import com.jetbrains.aspire.rider.util.getActiveDotNetCliPath
import com.jetbrains.aspire.rider.util.getStartBrowserAction
import com.jetbrains.aspire.rider.util.toProjectRunProperties
import com.jetbrains.aspire.settings.AspireSettings
import com.jetbrains.rider.model.RunnableProject
import com.jetbrains.rider.model.RunnableProjectKind
import com.jetbrains.rider.model.runnableProjectsModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.run.configurations.TerminalMode
import com.jetbrains.rider.run.configurations.launchSettings.LaunchSettingsJson
import com.jetbrains.rider.runtime.DotNetExecutable
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntimeType
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

/**
 * Factory class for creating instances of [com.jetbrains.rider.runtime.DotNetExecutable] from an Aspire session request based on a regular .NET project.
 */
@ApiStatus.Internal
@Service(Service.Level.PROJECT)
internal class DotNetProjectSessionExecutableFactory(private val project: Project) {
    companion object {
        fun getInstance(project: Project) = project.service<DotNetProjectSessionExecutableFactory>()

        private val LOG = logger<DotNetProjectSessionExecutableFactory>()
    }

    suspend fun createExecutable(
        launchConfiguration: SessionProcessLaunchConfiguration,
        aspireRunConfiguration: AspireRiderRunConfiguration?,
        addBrowserAction: Boolean,
        runnableProjectKinds: List<RunnableProjectKind>
    ): SessionExecutable? {
        val sessionProjectPath = launchConfiguration.projectPath
        val runnableProject =
            project.solution.runnableProjectsModel.findByProjectPath(sessionProjectPath) {
                runnableProjectKinds.contains(it.kind)
            }

        return if (runnableProject != null) {
            getExecutableForRunnableProject(
                sessionProjectPath,
                runnableProject,
                launchConfiguration,
                aspireRunConfiguration,
                addBrowserAction
            )
        } else {
            getExecutableForExternalProject(
                sessionProjectPath,
                launchConfiguration,
                aspireRunConfiguration,
                addBrowserAction
            )
        }
    }

    private suspend fun getExecutableForRunnableProject(
        sessionProjectPath: Path,
        runnableProject: RunnableProject,
        launchConfiguration: SessionProcessLaunchConfiguration,
        aspireRunConfiguration: AspireRiderRunConfiguration?,
        addBrowserAction: Boolean
    ): SessionExecutable? {
        val output = runnableProject.projectOutputs.firstOrNull() ?: return null
        val launchProfile = getLaunchProfile(launchConfiguration, runnableProject, project)
        val executablePath = output.exePath
        val workingDirectory = getWorkingDirectory(launchProfile, output)
        val arguments = mergeArguments(launchConfiguration.args, output.defaultArguments, launchProfile?.commandLineArgs)
        val envs = mergeEnvironmentVariables(launchConfiguration.envs, launchProfile?.environmentVariables)

        val executableParams = getExecutableParams(
            sessionProjectPath,
            executablePath,
            workingDirectory,
            arguments,
            envs,
            output.tfm,
            project
        )

        val browserSettings = launchProfile?.let {
            getStartBrowserSettings(project, Path(runnableProject.projectFilePath), it, envs, output.tfm, aspireRunConfiguration)
        }
        val launchBrowser = AspireSettings.getInstance().doNotLaunchBrowserForProjects.not()
        val browserAction =
            if (launchBrowser && addBrowserAction && browserSettings != null && aspireRunConfiguration != null) {
                getStartBrowserAction(aspireRunConfiguration, browserSettings)
            } else {
                { _, _, _ -> }
            }

        LOG.trace { "Executable parameters for runnable project (${runnableProject.projectFilePath}): $executableParams" }
        LOG.trace { "Browser settings for runnable project (${runnableProject.projectFilePath}): $browserSettings" }

        val executable = DotNetExecutable(
            executableParams.executablePath ?: executablePath,
            executableParams.tfm ?: output.tfm,
            executableParams.workingDirectoryPath ?: workingDirectory,
            executableParams.commandLineArgumentString ?: arguments,
            TerminalMode.Auto,
            executableParams.environmentVariables,
            false,
            browserAction,
            null,
            "",
            !executablePath.endsWith(".dll", true),
            DotNetCoreRuntimeType,
        )

        return SessionExecutable(executable, browserSettings)
    }

    private suspend fun getExecutableForExternalProject(
        sessionProjectPath: Path,
        launchConfiguration: SessionProcessLaunchConfiguration,
        aspireRunConfiguration: AspireRiderRunConfiguration?,
        addBrowserAction: Boolean
    ): SessionExecutable? {
        val propertyService = MSBuildPropertyService.getInstance(project)
        val runProperties = propertyService.getProjectRunProperties(sessionProjectPath).getOrElse {
            LOG.warn("Unable to get MSBuild run properties of $sessionProjectPath", it)
            return null
        }
        val dotNetCliPath = getActiveDotNetCliPath(project).getOrElse {
            LOG.warn("Unable to resolve MSBuild run command of $sessionProjectPath", it)
            return null
        }
        val properties = runProperties.toProjectRunProperties(sessionProjectPath, dotNetCliPath).getOrElse {
            LOG.warn("Unable to resolve MSBuild run command of $sessionProjectPath", it)
            return null
        }
        val launchProfile = getLaunchProfile(launchConfiguration, sessionProjectPath, project)

        return createExecutableFromRunProperties(
            launchConfiguration,
            launchProfile,
            properties,
            aspireRunConfiguration,
            addBrowserAction
        )
    }

    /**
     * Creates an executable that runs the session project as described by the MSBuild run [properties].
     * The executable can run under the debugger only if the MSBuild command runs the project output.
     */
    suspend fun createExecutableFromRunProperties(
        launchConfiguration: SessionProcessLaunchConfiguration,
        launchProfile: LaunchSettingsJson.Profile?,
        properties: ProjectRunProperties,
        aspireRunConfiguration: AspireRiderRunConfiguration?,
        addBrowserAction: Boolean
    ): SessionExecutable {
        val sessionProjectPath = launchConfiguration.projectPath
        val runCommand = properties.runCommand
        val executablePath = runCommand.executablePath.systemIndependentPath
        val workingDirectory = getWorkingDirectory(launchProfile, properties)
        val arguments = mergeArguments(launchConfiguration.args, runCommand.arguments, launchProfile?.commandLineArgs)
        val envs = mergeEnvironmentVariables(launchConfiguration.envs, launchProfile?.environmentVariables)

        val executableParams = getExecutableParams(
            sessionProjectPath,
            executablePath,
            workingDirectory,
            arguments,
            envs,
            properties.targetFramework,
            project
        )

        val browserSettings = launchProfile?.let {
            getStartBrowserSettings(project, sessionProjectPath, it, envs, properties.targetFramework, aspireRunConfiguration)
        }
        val launchBrowser = AspireSettings.getInstance().doNotLaunchBrowserForProjects.not()
        val browserAction =
            if (launchBrowser && addBrowserAction && browserSettings != null && aspireRunConfiguration != null) {
                getStartBrowserAction(aspireRunConfiguration, browserSettings)
            } else {
                { _, _, _ -> }
            }

        LOG.trace { "Executable parameters for external project (${sessionProjectPath.absolutePathString()}): $executableParams" }
        LOG.trace { "Browser settings for external project (${sessionProjectPath.absolutePathString()}): $browserSettings" }

        val executable = DotNetExecutable(
            executableParams.executablePath ?: executablePath,
            executableParams.tfm ?: properties.targetFramework,
            executableParams.workingDirectoryPath ?: workingDirectory,
            executableParams.commandLineArgumentString ?: arguments,
            TerminalMode.Auto,
            executableParams.environmentVariables,
            false,
            browserAction,
            null,
            "",
            !executablePath.endsWith(".dll", true),
            DotNetCoreRuntimeType
        )

        return SessionExecutable(executable, browserSettings, runCommand.runsProjectOutput)
    }
}