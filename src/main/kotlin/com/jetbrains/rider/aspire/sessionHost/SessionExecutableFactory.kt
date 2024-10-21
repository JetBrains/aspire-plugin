@file:Suppress("DuplicatedCode")

package com.jetbrains.rider.aspire.sessionHost

import com.intellij.ide.browsers.StartBrowserSettings
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.io.systemIndependentPath
import com.jetbrains.rider.aspire.generated.SessionEnvironmentVariable
import com.jetbrains.rider.aspire.generated.SessionModel
import com.jetbrains.rider.aspire.run.AspireHostConfiguration
import com.jetbrains.rider.aspire.util.MSBuildPropertyService
import com.jetbrains.rider.aspire.util.getStartBrowserAction
import com.jetbrains.rider.model.RdTargetFrameworkId
import com.jetbrains.rider.model.RunnableProject
import com.jetbrains.rider.model.runnableProjectsModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.run.configurations.launchSettings.LaunchSettingsJson
import com.jetbrains.rider.run.configurations.launchSettings.LaunchSettingsJsonService
import com.jetbrains.rider.run.environment.ExecutableParameterProcessor
import com.jetbrains.rider.run.environment.ExecutableRunParameters
import com.jetbrains.rider.run.environment.ProjectProcessOptions
import com.jetbrains.rider.run.environment.StringProcessingParameters
import com.jetbrains.rider.runtime.DotNetExecutable
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntimeType
import java.io.File
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

@Service(Service.Level.PROJECT)
class SessionExecutableFactory(private val project: Project) {
    companion object {
        fun getInstance(project: Project) = project.service<SessionExecutableFactory>()

        private val LOG = logger<SessionExecutableFactory>()

        private const val DOTNET_LAUNCH_PROFILE = "DOTNET_LAUNCH_PROFILE"
    }

    suspend fun createExecutable(
        sessionModel: SessionModel,
        hostRunConfiguration: AspireHostConfiguration?,
        addBrowserAction: Boolean
    ): Pair<DotNetExecutable, StartBrowserSettings?>? {
        val sessionProjectPath = Path(sessionModel.projectPath)
        val runnableProject = project.solution.runnableProjectsModel.findBySessionProject(sessionProjectPath)

        return if (runnableProject != null) {
            getExecutableForRunnableProject(runnableProject, sessionModel, hostRunConfiguration, addBrowserAction)
        } else {
            getExecutableForExternalProject(sessionProjectPath, sessionModel, hostRunConfiguration, addBrowserAction)
        }
    }

    private suspend fun getExecutableForRunnableProject(
        runnableProject: RunnableProject,
        sessionModel: SessionModel,
        hostRunConfiguration: AspireHostConfiguration?,
        addBrowserAction: Boolean
    ): Pair<DotNetExecutable, StartBrowserSettings?>? {
        val output = runnableProject.projectOutputs.firstOrNull() ?: return null
        val launchProfile = getLaunchProfile(sessionModel, runnableProject)
        val executablePath = output.exePath
        val workingDirectory = output.workingDirectory
        val arguments = mergeArguments(sessionModel.args, output.defaultArguments, launchProfile?.commandLineArgs)
        val envs = mergeEnvironmentVariables(sessionModel.envs, launchProfile?.environmentVariables)

        val processOptions = ProjectProcessOptions(
            File(runnableProject.projectFilePath),
            File(workingDirectory)
        )
        val runParameters = ExecutableRunParameters(
            executablePath,
            workingDirectory,
            arguments,
            envs,
            true,
            output.tfm
        )

        val executableParams = ExecutableParameterProcessor
            .getInstance(project)
            .processEnvironment(runParameters, processOptions)

        val browserSettings = launchProfile?.let {
            getStartBrowserSettings(Path(runnableProject.projectFilePath), it, envs, output.tfm, hostRunConfiguration)
        }
        val browserAction = if (addBrowserAction && browserSettings != null && hostRunConfiguration != null) {
            getStartBrowserAction(hostRunConfiguration, browserSettings)
        } else {
            { _, _, _ -> }
        }

        LOG.trace { "Executable parameters for runnable project (${runnableProject.projectFilePath}): $executableParams" }
        LOG.trace { "Browser settings for runnable project (${runnableProject.projectFilePath}): $browserSettings" }

        return DotNetExecutable(
            executableParams.executablePath ?: executablePath,
            executableParams.tfm ?: output.tfm,
            executableParams.workingDirectoryPath ?: workingDirectory,
            executableParams.commandLineArgumentString ?: arguments,
            useMonoRuntime = false,
            useExternalConsole = false,
            executableParams.environmentVariables,
            false,
            browserAction,
            null,
            "",
            !executablePath.endsWith(".dll", true),
            DotNetCoreRuntimeType
        ) to browserSettings
    }

    private suspend fun getExecutableForExternalProject(
        sessionProjectPath: Path,
        sessionModel: SessionModel,
        hostRunConfiguration: AspireHostConfiguration?,
        addBrowserAction: Boolean
    ): Pair<DotNetExecutable, StartBrowserSettings?>? {
        val propertyService = MSBuildPropertyService.getInstance(project)
        val properties = propertyService.getProjectRunProperties(sessionProjectPath) ?: return null
        val launchProfile = getLaunchProfile(sessionModel, sessionProjectPath)
        val executablePath = properties.executablePath.systemIndependentPath
        val workingDirectory = properties.workingDirectory.systemIndependentPath
        val arguments = mergeArguments(sessionModel.args, properties.arguments, launchProfile?.commandLineArgs)
        val envs = mergeEnvironmentVariables(sessionModel.envs, launchProfile?.environmentVariables)

        val processOptions = ProjectProcessOptions(
            sessionProjectPath.toFile(),
            File(workingDirectory)
        )
        val runParameters = ExecutableRunParameters(
            executablePath,
            workingDirectory,
            arguments,
            envs,
            true,
            properties.targetFramework
        )

        val executableParams = ExecutableParameterProcessor
            .getInstance(project)
            .processEnvironment(runParameters, processOptions)

        val browserSettings = launchProfile?.let {
            getStartBrowserSettings(sessionProjectPath, it, envs, properties.targetFramework, hostRunConfiguration)
        }
        val browserAction = if (addBrowserAction && browserSettings != null && hostRunConfiguration != null) {
            getStartBrowserAction(hostRunConfiguration, browserSettings)
        } else {
            { _, _, _ -> }
        }

        LOG.trace { "Executable parameters for external project (${sessionProjectPath.absolutePathString()}): $executableParams" }
        LOG.trace { "Browser settings for external project (${sessionProjectPath.absolutePathString()}): $browserSettings" }

        return DotNetExecutable(
            executableParams.executablePath ?: executablePath,
            executableParams.tfm ?: properties.targetFramework,
            executableParams.workingDirectoryPath ?: workingDirectory,
            executableParams.commandLineArgumentString ?: arguments,
            useMonoRuntime = false,
            useExternalConsole = false,
            executableParams.environmentVariables,
            false,
            browserAction,
            null,
            "",
            !executablePath.endsWith(".dll", true),
            DotNetCoreRuntimeType
        ) to browserSettings
    }

    //See: https://github.com/dotnet/aspire/blob/main/docs/specs/IDE-execution.md#launch-profile-processing-project-launch-configuration
    private suspend fun getLaunchProfile(
        sessionModel: SessionModel,
        runnableProject: RunnableProject
    ): LaunchSettingsJson.Profile? {
        val launchProfileKey = getLaunchProfileKey(sessionModel) ?: return null

        val launchSettings = readAction {
            LaunchSettingsJsonService.loadLaunchSettings(runnableProject)
        } ?: return null

        return launchSettings.profiles?.get(launchProfileKey)
    }

    //See: https://github.com/dotnet/aspire/blob/main/docs/specs/IDE-execution.md#launch-profile-processing-project-launch-configuration
    private suspend fun getLaunchProfile(
        sessionModel: SessionModel,
        sessionProjectPath: Path
    ): LaunchSettingsJson.Profile? {
        val launchProfileKey = getLaunchProfileKey(sessionModel) ?: return null

        val launchSettingsFile =
            LaunchSettingsJsonService.getLaunchSettingsFileForProject(sessionProjectPath.toFile()) ?: return null
        val launchSettings = readAction {
            LaunchSettingsJsonService.loadLaunchSettings(launchSettingsFile)
        } ?: return null

        return launchSettings.profiles?.get(launchProfileKey)
    }

    private fun getLaunchProfileKey(sessionModel: SessionModel): String? {
        if (sessionModel.disableLaunchProfile) {
            LOG.trace { "Launch profile disabled" }
            return null
        }

        val launchProfileKey =
            if (!sessionModel.launchProfile.isNullOrEmpty()) {
                sessionModel.launchProfile
            } else {
                sessionModel.envs?.firstOrNull { it.key.equals(DOTNET_LAUNCH_PROFILE, false) }?.value
            }

        LOG.trace { "Found launch profile key: $launchProfileKey" }

        return launchProfileKey
    }

    //See: https://github.com/dotnet/aspire/blob/main/docs/specs/IDE-execution.md#launch-profile-processing-project-launch-configuration
    private fun mergeArguments(
        sessionArguments: Array<String>?,
        defaultArguments: List<String>,
        launchProfileArguments: String?
    ) = buildString {
        if (sessionArguments != null) {
            if (sessionArguments.isNotEmpty()) {
                append(ParametersListUtil.join(sessionArguments.toList()))
            }
        } else {
            if (defaultArguments.isNotEmpty()) {
                append(ParametersListUtil.join(defaultArguments))
                append(" ")
            }

            if (!launchProfileArguments.isNullOrEmpty()) {
                append(launchProfileArguments)
            }
        }
    }

    //See: https://github.com/dotnet/aspire/blob/main/docs/specs/IDE-execution.md#launch-profile-processing-project-launch-configuration
    private fun mergeEnvironmentVariables(
        sessionEnvironmentVariables: Array<SessionEnvironmentVariable>?,
        launchProfileEnvironmentVariables: Map<String, String?>?
    ) = buildMap {
        if (launchProfileEnvironmentVariables?.isNotEmpty() == true) {
            launchProfileEnvironmentVariables.forEach {
                it.value?.let { value -> put(it.key, value) }
            }
        }

        if (sessionEnvironmentVariables?.isNotEmpty() == true) {
            sessionEnvironmentVariables.associateTo(this) { it.key to it.value }
        }
    }

    private suspend fun getStartBrowserSettings(
        projectFilePath: Path,
        launchProfile: LaunchSettingsJson.Profile,
        envs: Map<String, String>,
        tfm: RdTargetFrameworkId?,
        hostRunConfiguration: AspireHostConfiguration?
    ): StartBrowserSettings {
        val applicationUrlKey = "ApplicationUrl"
        val applicationRawUrl = launchProfile.applicationUrl
        val launchUrlKey = "LaunchUrl"
        val launchRawUrl = launchProfile.launchUrl
        val params = StringProcessingParameters(
            mapOf(applicationUrlKey to applicationRawUrl, launchUrlKey to launchRawUrl),
            true,
            envs,
            tfm
        )
        val projectOptions = ProjectProcessOptions(
            projectFilePath.toFile(),
            null
        )
        val parameterProcessor = ExecutableParameterProcessor.getInstance(project)
        val processedParams = parameterProcessor.processStrings(params, projectOptions)
        val launchUrl = processedParams[launchUrlKey]
        val applicationUrl = processedParams[applicationUrlKey]?.split(';')?.firstOrNull()
        val browserUrl = concatUrl(applicationUrl, launchUrl)
        val webBrowser = hostRunConfiguration?.parameters?.startBrowserParameters?.browser

        return StartBrowserSettings().apply {
            browser = webBrowser
            isSelected = launchProfile.launchBrowser
            url = browserUrl
        }
    }

    private fun concatUrl(part1: String?, part2: String?): String? {
        if (part1.isNullOrEmpty() || part2?.let(::isAbsoluteUrl) == true) return part2
        if (part2.isNullOrEmpty()) return part1
        return "$part1/$part2"
    }

    private fun isAbsoluteUrl(url: String): Boolean {
        return try {
            val uri = URI(url)
            uri.isAbsolute
        } catch (_: URISyntaxException) {
            false
        }
    }
}