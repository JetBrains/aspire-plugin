package com.jetbrains.aspire.rider.processLaunchers.externalBuild

import com.intellij.execution.ExecutionException
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.rider.processLaunchers.DotNetProjectSessionExecutableFactory
import com.jetbrains.aspire.rider.processLaunchers.SessionExecutable
import com.jetbrains.aspire.rider.processLaunchers.SessionProcessLaunchConfiguration
import com.jetbrains.aspire.rider.processLaunchers.executableLibrary.ExecutableLibraryExecutableFactory
import com.jetbrains.aspire.rider.processLaunchers.getLaunchProfile
import com.jetbrains.aspire.rider.run.AspireRiderRunConfiguration
import com.jetbrains.aspire.rider.util.MSBuildPropertyService
import com.jetbrains.aspire.rider.util.getActiveDotNetCliPath
import com.jetbrains.aspire.rider.util.parseTargetFrameworkId
import com.jetbrains.aspire.rider.util.toProjectRunProperties
import com.jetbrains.rider.run.configurations.launchSettings.commands.ExecutableCommand
import com.jetbrains.rider.runtime.DotNetExecutable

/**
 * Factory class for creating instances of [DotNetExecutable] from an Aspire session request
 * based on a .NET project that was built externally by the AppHost.
 *
 * The project isn't built; the executable runs the command that MSBuild resolves for the same build inputs.
 */
@Service(Service.Level.PROJECT)
internal class ExternalBuildSessionExecutableFactory(private val project: Project) {
    companion object {
        fun getInstance(project: Project) = project.service<ExternalBuildSessionExecutableFactory>()

        private val LOG = logger<ExternalBuildSessionExecutableFactory>()
    }

    suspend fun createExecutable(
        launchConfiguration: SessionProcessLaunchConfiguration,
        aspireRunConfiguration: AspireRiderRunConfiguration?
    ): Result<SessionExecutable> {
        val projectPath = launchConfiguration.projectPath
        val buildSettings = checkNotNull(launchConfiguration.externalBuild) {
            "External build settings are missing for $projectPath"
        }

        val propertyService = MSBuildPropertyService.getInstance(project)
        val launchProfile = getLaunchProfile(launchConfiguration, projectPath, project)
        val properties = propertyService.getExternallyBuiltProjectRunProperties(
            projectPath,
            buildSettings.configuration,
            buildSettings.environment,
            buildSettings.workingDirectory
        ).getOrElse {
            LOG.warn("Unable to get MSBuild run properties of $projectPath", it)
            return Result.failure(it)
        }

        // The `Executable` launch profile chosen by the user replaces the MSBuild command
        if (launchProfile != null && launchProfile.commandName.equals(ExecutableCommand.COMMAND_NAME, true)) {
            val targetFramework = parseTargetFrameworkId(properties.targetFramework)
            val executable = ExecutableLibraryExecutableFactory.getInstance(project)
                .createExecutable(launchConfiguration, launchProfile, targetFramework)
            if (executable == null) {
                LOG.warn("Unable to create an executable from the launch profile of $projectPath")
                return Result.failure(ExecutionException("Unable to create an executable from the launch profile of $projectPath"))
            }

            return Result.success(SessionExecutable(executable))
        }

        val dotNetCliPath = getActiveDotNetCliPath(project).getOrElse {
            LOG.warn("Unable to resolve MSBuild run command of $projectPath", it)
            return Result.failure(it)
        }
        val runProperties = properties.toProjectRunProperties(projectPath, dotNetCliPath).getOrElse {
            LOG.warn("Unable to resolve MSBuild run command of $projectPath", it)
            return Result.failure(it)
        }
        val sessionExecutable = DotNetProjectSessionExecutableFactory.getInstance(project)
            .createExecutableFromRunProperties(
                launchConfiguration,
                launchProfile,
                runProperties,
                aspireRunConfiguration,
                true
            )

        return Result.success(sessionExecutable)
    }
}
