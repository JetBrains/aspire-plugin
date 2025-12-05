package com.jetbrains.aspire.rider.sessions

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.util.execution.ParametersListUtil
import com.jetbrains.aspire.sessions.DotNetSessionLaunchConfiguration
import com.jetbrains.rider.model.RdTargetFrameworkId
import com.jetbrains.rider.model.RunnableProject
import com.jetbrains.rider.run.configurations.launchSettings.LaunchSettingsJson
import com.jetbrains.rider.run.configurations.launchSettings.LaunchSettingsJsonService
import com.jetbrains.rider.run.environment.ExecutableParameterProcessingResult
import com.jetbrains.rider.run.environment.ExecutableParameterProcessor
import com.jetbrains.rider.run.environment.ExecutableRunParameters
import com.jetbrains.rider.run.environment.ProjectProcessOptions
import java.nio.file.Path
import kotlin.io.path.Path

private const val DOTNET_LAUNCH_PROFILE = "DOTNET_LAUNCH_PROFILE"
private val LOG = Logger.getInstance("#com.jetbrains.aspire.rider.sessions.SessionExecutableFactoryUtils")

//See: https://github.com/dotnet/aspire/blob/main/docs/specs/IDE-execution.md#launch-profile-processing-project-launch-configuration
suspend fun getLaunchProfile(
    launchConfiguration: DotNetSessionLaunchConfiguration,
    runnableProject: RunnableProject,
    project: Project
): LaunchSettingsJson.Profile? {
    val launchProfileKey = getLaunchProfileKey(launchConfiguration) ?: return null

    val launchSettings = LaunchSettingsJsonService
        .getInstance(project)
        .loadLaunchSettingsSuspend(runnableProject)
        ?: return null

    return launchSettings.profiles?.get(launchProfileKey)
}

//See: https://github.com/dotnet/aspire/blob/main/docs/specs/IDE-execution.md#launch-profile-processing-project-launch-configuration
suspend fun getLaunchProfile(
    launchConfiguration: DotNetSessionLaunchConfiguration,
    sessionProjectPath: Path,
    project: Project
): LaunchSettingsJson.Profile? {
    val launchProfileKey = getLaunchProfileKey(launchConfiguration) ?: return null

    val launchSettingsFile =
        LaunchSettingsJsonService.getLaunchSettingsFileForProject(sessionProjectPath) ?: return null
    val launchSettings =
        LaunchSettingsJsonService.getInstance(project).loadLaunchSettingsSuspend(launchSettingsFile) ?: return null

    return launchSettings.profiles?.get(launchProfileKey)
}

private fun getLaunchProfileKey(launchConfiguration: DotNetSessionLaunchConfiguration): String? {
    if (launchConfiguration.disableLaunchProfile) {
        LOG.trace { "Launch profile disabled" }
        return null
    }

    val launchProfileKey =
        if (!launchConfiguration.launchProfile.isNullOrEmpty()) {
            launchConfiguration.launchProfile
        } else {
            launchConfiguration.envs?.firstOrNull { it.first.equals(DOTNET_LAUNCH_PROFILE, false) }?.second
        }

    LOG.trace { "Found launch profile key: $launchProfileKey" }

    return launchProfileKey
}

//See: https://github.com/dotnet/aspire/blob/main/docs/specs/IDE-execution.md#launch-profile-processing-project-launch-configuration
fun mergeArguments(
    sessionArguments: List<String>?,
    defaultArguments: List<String>,
    launchProfileArguments: String?
) = buildString {
    if (defaultArguments.isNotEmpty()) {
        append(ParametersListUtil.join(defaultArguments))
        append(" ")
    }

    if (sessionArguments != null) {
        if (sessionArguments.isNotEmpty()) {
            append(ParametersListUtil.join(sessionArguments.toList()))
        }
    } else {
        if (!launchProfileArguments.isNullOrEmpty()) {
            append(launchProfileArguments)
        }
    }
}

//See: https://github.com/dotnet/aspire/blob/main/docs/specs/IDE-execution.md#launch-profile-processing-project-launch-configuration
fun mergeEnvironmentVariables(
    sessionEnvironmentVariables: List<Pair<String, String>>?,
    launchProfileEnvironmentVariables: Map<String, String?>?
) = buildMap {
    if (launchProfileEnvironmentVariables?.isNotEmpty() == true) {
        launchProfileEnvironmentVariables.forEach {
            it.value?.let { value -> put(it.key, value) }
        }
    }

    if (sessionEnvironmentVariables?.isNotEmpty() == true) {
        sessionEnvironmentVariables.associateTo(this) { it.first to it.second }
    }
}

suspend fun getExecutableParams(
    sessionProjectPath: Path,
    executablePath: String,
    workingDirectory: String,
    arguments: String,
    envs: Map<String, String>,
    targetFramework: RdTargetFrameworkId?,
    project: Project
): ExecutableParameterProcessingResult {
    val processOptions = ProjectProcessOptions(
        sessionProjectPath,
        Path(workingDirectory)
    )
    val runParameters = ExecutableRunParameters(
        executablePath,
        workingDirectory,
        arguments,
        envs,
        true,
        targetFramework
    )

    return ExecutableParameterProcessor
        .getInstance(project)
        .processEnvironment(runParameters, processOptions)
}