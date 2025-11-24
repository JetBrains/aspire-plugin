package com.jetbrains.rider.aspire.run.host

import com.intellij.execution.CantRunException
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project
import com.intellij.util.execution.ParametersListUtil
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.aspire.launchProfiles.getApplicationUrl
import com.jetbrains.rider.aspire.launchProfiles.getArguments
import com.jetbrains.rider.aspire.launchProfiles.getEnvironmentVariables
import com.jetbrains.rider.aspire.launchProfiles.getLaunchBrowserFlag
import com.jetbrains.rider.aspire.launchProfiles.getProjectLaunchProfileByName
import com.jetbrains.rider.aspire.launchProfiles.getWorkingDirectory
import com.jetbrains.rider.aspire.run.AspireExecutorFactory
import com.jetbrains.rider.aspire.run.AspireRunnableProjectKinds
import com.jetbrains.rider.aspire.run.states.AspireHostDebugProfileState
import com.jetbrains.rider.aspire.run.states.AspireHostRunProfileState
import com.jetbrains.rider.aspire.util.getStartBrowserAction
import com.jetbrains.rider.model.ProjectOutput
import com.jetbrains.rider.model.RunnableProject
import com.jetbrains.rider.model.runnableProjectsModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.run.configurations.TerminalMode
import com.jetbrains.rider.run.configurations.controls.LaunchProfile
import com.jetbrains.rider.run.configurations.launchSettings.LaunchSettingsJsonService
import com.jetbrains.rider.run.configurations.project.DotNetProjectConfigurationParameters
import com.jetbrains.rider.run.environment.ExecutableParameterProcessor
import com.jetbrains.rider.run.environment.ExecutableRunParameters
import com.jetbrains.rider.run.environment.ProjectProcessOptions
import com.jetbrains.rider.runtime.DotNetExecutable
import com.jetbrains.rider.runtime.RiderDotNetActiveRuntimeHost
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntime
import kotlin.io.path.Path

internal class AspireHostExecutorFactory(
    private val project: Project,
    private val parameters: AspireHostConfigurationParameters
) : AspireExecutorFactory(project, parameters) {
    override suspend fun create(
        executorId: String,
        environment: ExecutionEnvironment,
        lifetime: Lifetime
    ): RunProfileState {
        val activeRuntime = RiderDotNetActiveRuntimeHost.getInstance(project).dotNetCoreRuntime.value
            ?: throw CantRunException("Unable to find appropriate dotnet runtime")

        val projects = project.solution.runnableProjectsModel.projects.valueOrNull
            ?: throw CantRunException(DotNetProjectConfigurationParameters.SOLUTION_IS_LOADING)

        val runnableProject = projects.singleOrNull {
            it.kind == AspireRunnableProjectKinds.AspireHost && it.projectFilePath == parameters.projectFilePath
        } ?: throw CantRunException(DotNetProjectConfigurationParameters.PROJECT_NOT_SPECIFIED)

        val projectOutput = runnableProject
            .projectOutputs
            .singleOrNull { it.tfm?.presentableName == parameters.projectTfm }
            ?: runnableProject.projectOutputs.firstOrNull()
            ?: throw CantRunException("Unable to get the project output for ${parameters.projectTfm}")

        val profile = LaunchSettingsJsonService
            .getInstance(project)
            .getProjectLaunchProfileByName(runnableProject, parameters.profileName)
            ?: throw CantRunException("Profile ${parameters.profileName} not found")

        val executable = getDotNetExecutable(runnableProject, projectOutput, profile, activeRuntime)

        return when (executorId) {
            DefaultRunExecutor.EXECUTOR_ID -> AspireHostRunProfileState(executable, activeRuntime, environment)
            DefaultDebugExecutor.EXECUTOR_ID -> AspireHostDebugProfileState(executable, activeRuntime, environment)
            else -> throw CantRunException("Unable to execute Aspire host with $executorId executor")
        }
    }

    private suspend fun getDotNetExecutable(
        runnableProject: RunnableProject,
        projectOutput: ProjectOutput,
        launchProfile: LaunchProfile,
        activeRuntime: DotNetCoreRuntime
    ): DotNetExecutable {
        val effectiveArguments =
            if (parameters.trackArguments) getArguments(launchProfile.content, projectOutput)
            else parameters.arguments

        val effectiveWorkingDirectory =
            if (parameters.trackWorkingDirectory) getWorkingDirectory(launchProfile.content, projectOutput)
            else parameters.workingDirectory

        val effectiveEnvs =
            if (parameters.trackEnvs) getEnvironmentVariables(launchProfile.name, launchProfile.content).toMutableMap()
            else parameters.envs.toMutableMap()
        val environmentVariableValues = configureEnvironmentVariables(effectiveEnvs, activeRuntime)

        var effectiveUrl =
            if (parameters.trackUrl) getApplicationUrl(launchProfile.content)
            else parameters.startBrowserParameters.url
        if (parameters.trackUrl && environmentVariableValues.browserToken != null) {
            effectiveUrl = configureUrl(effectiveUrl, environmentVariableValues.browserToken)
        }

        val effectiveLaunchBrowser =
            if (parameters.trackBrowserLaunch) getLaunchBrowserFlag(launchProfile.content)
            else parameters.startBrowserParameters.startAfterLaunch

        val processOptions = ProjectProcessOptions(
            Path(runnableProject.projectFilePath),
            Path(effectiveWorkingDirectory)
        )
        val runParameters = ExecutableRunParameters(
            projectOutput.exePath,
            effectiveWorkingDirectory,
            effectiveArguments,
            effectiveEnvs,
            true,
            projectOutput.tfm
        )

        val params = ExecutableParameterProcessor
            .getInstance(project)
            .processEnvironment(runParameters, processOptions)

        return DotNetExecutable(
            params.executablePath ?: projectOutput.exePath,
            params.tfm ?: projectOutput.tfm,
            params.workingDirectoryPath ?: projectOutput.workingDirectory,
            params.commandLineArgumentString ?: ParametersListUtil.join(projectOutput.defaultArguments),
            TerminalMode.Auto,
            params.environmentVariables,
            true,
            getStartBrowserAction(effectiveUrl, effectiveLaunchBrowser, parameters.startBrowserParameters),
            null,
            "",
            true
        )
    }
}