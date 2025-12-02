package com.jetbrains.aspire.rider.sessions

import com.intellij.ide.browsers.StartBrowserSettings
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.util.io.systemIndependentPath
import com.jetbrains.aspire.launchProfiles.getWorkingDirectory
import com.jetbrains.aspire.run.host.AspireHostConfiguration
import com.jetbrains.aspire.sessions.*
import com.jetbrains.aspire.settings.AspireSettings
import com.jetbrains.aspire.util.MSBuildPropertyService
import com.jetbrains.aspire.util.getStartBrowserAction
import com.jetbrains.rider.model.RdTargetFrameworkId
import com.jetbrains.rider.model.RunnableProject
import com.jetbrains.rider.model.runnableProjectsModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.run.configurations.RunnableProjectKinds
import com.jetbrains.rider.run.configurations.TerminalMode
import com.jetbrains.rider.run.configurations.launchSettings.LaunchSettingsJson
import com.jetbrains.rider.run.environment.ExecutableParameterProcessor
import com.jetbrains.rider.run.environment.ProjectProcessOptions
import com.jetbrains.rider.run.environment.StringProcessingParameters
import com.jetbrains.rider.runtime.DotNetExecutable
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntimeType
import org.jetbrains.annotations.ApiStatus
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

/**
 * Factory class for creating instances of [com.jetbrains.rider.runtime.DotNetExecutable] from an Aspire session request based on a regular .NET project.
 */
@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class DotNetProjectSessionExecutableFactory(private val project: Project) {
    companion object {
        fun getInstance(project: Project) = project.service<DotNetProjectSessionExecutableFactory>()

        private val LOG = logger<DotNetProjectSessionExecutableFactory>()
    }

    suspend fun createExecutable(
        launchConfiguration: DotNetSessionLaunchConfiguration,
        hostRunConfiguration: AspireHostConfiguration?,
        addBrowserAction: Boolean
    ): Pair<DotNetExecutable, StartBrowserSettings?>? {
        val sessionProjectPath = launchConfiguration.projectPath
        val runnableProject =
            project.solution.runnableProjectsModel.findBySessionProject(sessionProjectPath) { it.kind == RunnableProjectKinds.DotNetCore }

        return if (runnableProject != null) {
            getExecutableForRunnableProject(
                sessionProjectPath,
                runnableProject,
                launchConfiguration,
                hostRunConfiguration,
                addBrowserAction
            )
        } else {
            getExecutableForExternalProject(
                sessionProjectPath,
                launchConfiguration,
                hostRunConfiguration,
                addBrowserAction
            )
        }
    }

    private suspend fun getExecutableForRunnableProject(
        sessionProjectPath: Path,
        runnableProject: RunnableProject,
        launchConfiguration: DotNetSessionLaunchConfiguration,
        hostRunConfiguration: AspireHostConfiguration?,
        addBrowserAction: Boolean
    ): Pair<DotNetExecutable, StartBrowserSettings?>? {
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
            getStartBrowserSettings(Path(runnableProject.projectFilePath), it, envs, output.tfm, hostRunConfiguration)
        }
        val launchBrowser = AspireSettings.Companion.getInstance().doNotLaunchBrowserForProjects.not()
        val browserAction =
            if (launchBrowser && addBrowserAction && browserSettings != null && hostRunConfiguration != null) {
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
            TerminalMode.Auto,
            executableParams.environmentVariables,
            false,
            browserAction,
            null,
            "",
            !executablePath.endsWith(".dll", true),
            DotNetCoreRuntimeType,
        ) to browserSettings
    }

    private suspend fun getExecutableForExternalProject(
        sessionProjectPath: Path,
        launchConfiguration: DotNetSessionLaunchConfiguration,
        hostRunConfiguration: AspireHostConfiguration?,
        addBrowserAction: Boolean
    ): Pair<DotNetExecutable, StartBrowserSettings?>? {
        val propertyService = MSBuildPropertyService.getInstance(project)
        val properties = propertyService.getProjectRunProperties(sessionProjectPath) ?: return null
        val launchProfile = getLaunchProfile(launchConfiguration, sessionProjectPath, project)
        val executablePath = properties.executablePath.systemIndependentPath
        val workingDirectory = getWorkingDirectory(launchProfile, properties)
        val arguments = mergeArguments(launchConfiguration.args, properties.arguments, launchProfile?.commandLineArgs)
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
            getStartBrowserSettings(sessionProjectPath, it, envs, properties.targetFramework, hostRunConfiguration)
        }
        val launchBrowser = AspireSettings.Companion.getInstance().doNotLaunchBrowserForProjects.not()
        val browserAction =
            if (launchBrowser && addBrowserAction && browserSettings != null && hostRunConfiguration != null) {
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
            TerminalMode.Auto,
            executableParams.environmentVariables,
            false,
            browserAction,
            null,
            "",
            !executablePath.endsWith(".dll", true),
            DotNetCoreRuntimeType
        ) to browserSettings
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
            projectFilePath,
            null
        )
        val parameterProcessor = ExecutableParameterProcessor.getInstance(project)
        val processedParams = parameterProcessor.processStrings(params, projectOptions)
        val launchUrl = processedParams[launchUrlKey]
        val applicationUrl = processedParams[applicationUrlKey]?.split(';')?.firstOrNull()
        val browserUrl = concatUrl(applicationUrl, launchUrl)
        val webBrowser = hostRunConfiguration?.parameters?.startBrowserParameters?.browser
        val withJavaScriptDebugger =
            hostRunConfiguration?.parameters?.startBrowserParameters?.withJavaScriptDebugger == true

        return StartBrowserSettings().apply {
            browser = webBrowser
            isSelected = launchProfile.launchBrowser
            url = browserUrl
            isStartJavaScriptDebugger = withJavaScriptDebugger
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