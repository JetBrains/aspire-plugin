package com.jetbrains.aspire.rider.processLaunchers.executableLibrary

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.rider.processLaunchers.SessionProcessLaunchConfiguration
import com.jetbrains.aspire.rider.processLaunchers.getExecutableParams
import com.jetbrains.aspire.rider.processLaunchers.getLaunchProfile
import com.jetbrains.aspire.rider.processLaunchers.mergeEnvironmentVariables
import com.jetbrains.aspire.rider.util.MSBuildPropertyService
import com.jetbrains.rider.model.RdTargetFrameworkId
import com.jetbrains.rider.run.configurations.TerminalMode
import com.jetbrains.rider.run.configurations.launchSettings.LaunchSettingsJson
import com.jetbrains.rider.run.configurations.launchSettings.commands.ExecutableCommand
import com.jetbrains.rider.runtime.DotNetExecutable
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntimeType
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

/**
 * Factory class for creating instances of [DotNetExecutable] from an Aspire session request
 * based on a .NET library project that has an `Executable` launch profile.
 */
@Service(Service.Level.PROJECT)
internal class ExecutableLibraryExecutableFactory(private val project: Project) {
    companion object {
        fun getInstance(project: Project): ExecutableLibraryExecutableFactory = project.service()
        private val LOG = logger<ExecutableLibraryExecutableFactory>()
    }

    suspend fun createExecutable(launchConfiguration: SessionProcessLaunchConfiguration): DotNetExecutable? {
        val sessionProjectPath = launchConfiguration.projectPath

        val launchProfile = getLaunchProfile(launchConfiguration, sessionProjectPath, project)
        if (launchProfile == null) {
            LOG.warn("Unable to find launch profile for session project $sessionProjectPath")
            return null
        }

        if (!launchProfile.commandName.equals(ExecutableCommand.COMMAND_NAME, true)) {
            LOG.warn("Launch profile command name for library project should be `${ExecutableCommand.COMMAND_NAME}`")
            return null
        }

        val targetFramework = MSBuildPropertyService.getInstance(project).getProjectTargetFramework(sessionProjectPath)
            .getOrElse {
                LOG.warn("Unable to get target framework of $sessionProjectPath", it)
                null
            }

        return createExecutable(launchConfiguration, launchProfile, targetFramework)
    }

    /**
     * Creates an executable from the `Executable` [launchProfile] of the session project.
     */
    suspend fun createExecutable(
        launchConfiguration: SessionProcessLaunchConfiguration,
        launchProfile: LaunchSettingsJson.Profile,
        targetFramework: RdTargetFrameworkId?
    ): DotNetExecutable? {
        val sessionProjectPath = launchConfiguration.projectPath

        val executablePath = launchProfile.executablePath
        val arguments = launchProfile.commandLineArgs ?: ""

        if (executablePath.isNullOrEmpty()) {
            LOG.warn("Launch profile executable path is empty or null")
            return null
        }

        val workingDirectory = launchProfile.workingDirectory.takeUnless { it.isNullOrEmpty() }
            ?: sessionProjectPath.parent.absolutePathString()

        val workingDirectoryAbsolutePath = if (workingDirectory.startsWith("$(")) {
            workingDirectory
        } else {
            val workingDirectoryPath = Path(workingDirectory)
            if (!workingDirectoryPath.isAbsolute) {
                sessionProjectPath.parent.resolve(workingDirectoryPath.normalize()).absolutePathString()
            } else {
                workingDirectoryPath.absolutePathString()
            }
        }

        val envs = mergeEnvironmentVariables(launchConfiguration.envs, launchProfile.environmentVariables)

        val executableParams = getExecutableParams(
            sessionProjectPath,
            executablePath,
            workingDirectoryAbsolutePath,
            arguments,
            envs,
            targetFramework,
            project
        )

        LOG.trace { "Executable parameters for the project (${sessionProjectPath.absolutePathString()}): $executableParams" }

        return DotNetExecutable(
            executableParams.executablePath ?: executablePath,
            targetFramework,
            executableParams.workingDirectoryPath ?: workingDirectoryAbsolutePath,
            executableParams.commandLineArgumentString ?: arguments,
            TerminalMode.Auto,
            executableParams.environmentVariables,
            true,
            { _, _, _ -> },
            null,
            "",
            true,
            DotNetCoreRuntimeType
        )
    }
}