package com.jetbrains.rider.aspire.sessions.executableLibrary

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.jetbrains.rider.aspire.generated.CreateSessionRequest
import com.jetbrains.rider.aspire.sessions.getExecutableParams
import com.jetbrains.rider.aspire.sessions.getLaunchProfile
import com.jetbrains.rider.aspire.sessions.mergeEnvironmentVariables
import com.jetbrains.rider.aspire.util.MSBuildPropertyService
import com.jetbrains.rider.run.configurations.launchSettings.commands.ExecutableCommand
import com.jetbrains.rider.runtime.DotNetExecutable
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntimeType
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

/**
 * Factory class for creating instances of [DotNetExecutable] from a .NET library project
 * that has an `Executable` launch profile.
 */
@Service(Service.Level.PROJECT)
class ExecutableLibraryExecutableFactory(private val project: Project) {
    companion object {
        fun getInstance(project: Project): ExecutableLibraryExecutableFactory = project.service()
        private val LOG = logger<ExecutableLibraryExecutableFactory>()
    }

    suspend fun createExecutable(sessionModel: CreateSessionRequest): DotNetExecutable? {
        val sessionProjectPath = Path(sessionModel.projectPath)

        val launchProfile = getLaunchProfile(sessionModel, sessionProjectPath, project)
        if (launchProfile == null) {
            LOG.warn("Unable to find launch profile for session project $sessionProjectPath")
            return null
        }

        if (!launchProfile.commandName.equals(ExecutableCommand.COMMAND_NAME, true)) {
            LOG.warn("Launch profile command name for library project should be `${ExecutableCommand.COMMAND_NAME}`")
            return null
        }

        val executablePath = launchProfile.executablePath
        val workingDirectory = launchProfile.workingDirectory
        val arguments = launchProfile.commandLineArgs

        if (executablePath.isNullOrEmpty() || workingDirectory.isNullOrEmpty() || arguments.isNullOrEmpty()) {
            LOG.warn("Some launch profile properties are empty or null")
            return null
        }

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

        val envs = mergeEnvironmentVariables(sessionModel.envs, launchProfile.environmentVariables)

        val targetFramework = MSBuildPropertyService.getInstance(project).getProjectTargetFramework(sessionProjectPath)

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
            useMonoRuntime = false,
            useExternalConsole = false,
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