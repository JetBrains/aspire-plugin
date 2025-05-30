package com.jetbrains.rider.aspire.run

import com.intellij.execution.CantRunException
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.EnvironmentUtil
import com.intellij.util.execution.ParametersListUtil
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.aspire.launchProfiles.*
import com.jetbrains.rider.aspire.run.states.AspireHostDebugProfileState
import com.jetbrains.rider.aspire.run.states.AspireHostRunProfileState
import com.jetbrains.rider.aspire.sessionHost.SessionHostManager
import com.jetbrains.rider.aspire.util.*
import com.jetbrains.rider.model.ProjectOutput
import com.jetbrains.rider.model.RunnableProject
import com.jetbrains.rider.model.runnableProjectsModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.run.configurations.AsyncExecutorFactory
import com.jetbrains.rider.run.configurations.controls.LaunchProfile
import com.jetbrains.rider.run.configurations.launchSettings.LaunchSettingsJsonService
import com.jetbrains.rider.run.configurations.project.DotNetProjectConfigurationParameters
import com.jetbrains.rider.run.environment.ExecutableParameterProcessor
import com.jetbrains.rider.run.environment.ExecutableRunParameters
import com.jetbrains.rider.run.environment.ProjectProcessOptions
import com.jetbrains.rider.runtime.DotNetExecutable
import com.jetbrains.rider.runtime.RiderDotNetActiveRuntimeHost
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntime
import com.jetbrains.rider.util.NetUtils
import com.jetbrains.rider.utils.RiderEnvironmentAccessor
import java.net.URI
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

class AspireHostExecutorFactory(
    private val project: Project,
    private val parameters: AspireHostConfigurationParameters
) : AsyncExecutorFactory {
    companion object {
        private const val DOTNET_ROOT = "DOTNET_ROOT"

    }

    override suspend fun create(
        executorId: String,
        environment: ExecutionEnvironment,
        lifetime: Lifetime
    ): RunProfileState {
        val activeRuntime = RiderDotNetActiveRuntimeHost.getInstance(project).dotNetCoreRuntime.value
            ?: throw CantRunException("Unable to find appropriate runtime")

        val projects = project.solution.runnableProjectsModel.projects.valueOrNull
            ?: throw CantRunException(DotNetProjectConfigurationParameters.SOLUTION_IS_LOADING)

        val runnableProject = projects.singleOrNull {
            it.kind == AspireRunnableProjectKinds.AspireHost && it.projectFilePath == parameters.projectFilePath
        } ?: throw CantRunException(DotNetProjectConfigurationParameters.PROJECT_NOT_SPECIFIED)

        val projectOutput = runnableProject
            .projectOutputs
            .singleOrNull { it.tfm?.presentableName == parameters.projectTfm }
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
            useMonoRuntime = false,
            useExternalConsole = false,
            params.environmentVariables,
            true,
            getStartBrowserAction(effectiveUrl, effectiveLaunchBrowser, parameters.startBrowserParameters),
            null,
            "",
            true
        )
    }

    private suspend fun configureEnvironmentVariables(
        envs: MutableMap<String, String>,
        activeRuntime: DotNetCoreRuntime
    ): EnvironmentVariableValues {
        val sessionHost = SessionHostManager.getInstance(project).getOrStartSessionHost()

        //Switch DCP to the IDE mode
        //see: https://github.com/dotnet/aspire/blob/main/docs/specs/IDE-execution.md#enabling-ide-execution
        val debugSessionToken = requireNotNull(sessionHost.debugSessionToken)
        val debugSessionPort = requireNotNull(sessionHost.debugSessionPort)
        val dcpInstancePrefix = generateDcpInstancePrefix()
        envs[DEBUG_SESSION_TOKEN] = debugSessionToken
        envs[DEBUG_SESSION_PORT] = "localhost:$debugSessionPort"
        envs[DCP_INSTANCE_ID_PREFIX] = dcpInstancePrefix

        val urls = requireNotNull(envs[ASPNETCORE_URLS])
        val isHttpUrl = !urls.contains("https")
        val allowUnsecuredTransport = envs.getAspireAllowUnsecuredTransport()

        //Automatically set the `ASPIRE_ALLOW_UNSECURED_TRANSPORT` environment variable if the `http` protocol is used
        //see: https://learn.microsoft.com/en-us/dotnet/aspire/app-host/configuration#common-configuration
        //see: https://learn.microsoft.com/en-us/dotnet/aspire/troubleshooting/allow-unsecure-transport
        if (isHttpUrl && !allowUnsecuredTransport) {
            envs[ASPIRE_ALLOW_UNSECURED_TRANSPORT] = "true"
        }

        val useHttp = isHttpUrl || allowUnsecuredTransport

        //Set the `DOTNET_RESOURCE_SERVICE_ENDPOINT_URL` environment variable if not specified to connect to the resource service
        //see: https://learn.microsoft.com/en-us/dotnet/aspire/app-host/configuration#resource-service
        //see: https://learn.microsoft.com/en-us/dotnet/aspire/fundamentals/dashboard/configuration?tabs=bash#common-configuration
        //note: we have to replace `ASPIRE_RESOURCE_SERVICE_ENDPOINT_URL` with `DOTNET_RESOURCE_SERVICE_ENDPOINT_URL`
        //otherwise the url won't be passed to the resource service
        if (!envs.containsKey(DOTNET_RESOURCE_SERVICE_ENDPOINT_URL)) {
            val aspireResourceServiceEndpoint = envs[ASPIRE_RESOURCE_SERVICE_ENDPOINT_URL]
            if (!aspireResourceServiceEndpoint.isNullOrEmpty()) {
                envs[DOTNET_RESOURCE_SERVICE_ENDPOINT_URL] = aspireResourceServiceEndpoint
            } else {
                val resourceEndpointPort = NetUtils.findFreePort(47200)
                envs[DOTNET_RESOURCE_SERVICE_ENDPOINT_URL] =
                    if (useHttp) "http://localhost:$resourceEndpointPort"
                    else "https://localhost:$resourceEndpointPort"
            }
        }

        val allowAnonymousDashboard = envs.getAspireDashboardUnsecuredAllowAnonymous()

        //Set the `ASPIRE_DASHBOARD_FRONTEND_BROWSERTOKEN` environment variable to open a dashboard without login
        //see: https://learn.microsoft.com/en-us/dotnet/aspire/app-host/configuration#dashboard
        //see: https://learn.microsoft.com/en-us/dotnet/aspire/fundamentals/dashboard/configuration#frontend-authentication
        var browserToken: String? = null
        if (!allowAnonymousDashboard) {
            browserToken = UUID.randomUUID().toString()
            envs[ASPIRE_DASHBOARD_FRONTEND_BROWSERTOKEN] = browserToken
        }

        //Set the `ASPIRE_DASHBOARD_RESOURCESERVICE_APIKEY` environment variable to configure resource service API key
        //see: https://learn.microsoft.com/en-us/dotnet/aspire/app-host/configuration#resource-service
        //see: https://learn.microsoft.com/en-us/dotnet/aspire/fundamentals/dashboard/configuration#resources
        if (!allowAnonymousDashboard) {
            val apiKey = UUID.randomUUID().toString()
            envs[ASPIRE_DASHBOARD_RESOURCESERVICE_APIKEY] = apiKey
        }

        //Set `ASPIRE_CONTAINER_RUNTIME` environment variable to `podman` if it is specified in the run parameters
        //see: https://learn.microsoft.com/en-us/dotnet/aspire/app-host/configuration#common-configuration
        val containerRuntime = envs.getAspireContainerRuntime()
        if (parameters.usePodmanRuntime && !containerRuntime.equals("podman", true)) {
            envs[ASPIRE_CONTAINER_RUNTIME] = "podman"
        }

        val dotnetPath = RiderEnvironmentAccessor.getInstance(project).findFileInSystemPath("dotnet")
        if (dotnetPath == null) {
            setDotnetRootPathVariable(envs, activeRuntime)
        }

        return EnvironmentVariableValues(browserToken)
    }

    private fun setDotnetRootPathVariable(envs: MutableMap<String, String>, activeRuntime: DotNetCoreRuntime) {
        val dotnetRootPath = Path(activeRuntime.cliExePath).parent

        val dotnetRootPathString = dotnetRootPath.absolutePathString()
        val dotnetToolsPathString = dotnetRootPath.resolve("tools").absolutePathString()
        val dotnetPaths =
            if (SystemInfo.isUnix) "$dotnetRootPathString:$dotnetToolsPathString"
            else "$dotnetRootPathString;$dotnetToolsPathString"

        val pathVariable = PathEnvironmentVariableUtil.getPathVariableValue()
        if (pathVariable != null) {
            envs[RiderEnvironmentAccessor.PATH_VARIABLE] =
                if (SystemInfo.isUnix) "$pathVariable:$dotnetPaths"
                else "$pathVariable;$dotnetPaths"
        } else {
            envs[RiderEnvironmentAccessor.PATH_VARIABLE] = dotnetPaths
        }

        val dotnetRootEnvironmentVariable = EnvironmentUtil.getValue(DOTNET_ROOT)
        if (dotnetRootEnvironmentVariable == null) {
            envs[DOTNET_ROOT] = dotnetRootPathString
        }
    }

    private fun configureUrl(urlValue: String, browserToken: String): String {
        val url = URI(urlValue)
        val updatedUrl = URI(
            url.scheme,
            null,
            url.host,
            url.port,
            "/login",
            "t=${browserToken}",
            null
        )
        return updatedUrl.toString()
    }

    private data class EnvironmentVariableValues(
        val browserToken: String?
    )
}