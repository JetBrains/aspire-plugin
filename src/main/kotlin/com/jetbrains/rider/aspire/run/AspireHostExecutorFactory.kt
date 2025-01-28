package com.jetbrains.rider.aspire.run

import com.intellij.execution.CantRunException
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project
import com.intellij.util.execution.ParametersListUtil
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.aspire.launchProfiles.*
import com.jetbrains.rider.aspire.run.states.AspireHostDebugProfileState
import com.jetbrains.rider.aspire.run.states.AspireHostRunProfileState
import com.jetbrains.rider.aspire.sessionHost.SessionHostManager2
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
import com.jetbrains.rider.util.NetUtils
import java.io.File
import java.net.URI
import java.util.*

class AspireHostExecutorFactory(
    private val project: Project,
    private val parameters: AspireHostConfigurationParameters
) : AsyncExecutorFactory {
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

        val executable = getDotNetExecutable(runnableProject, projectOutput, profile)

        return when (executorId) {
            DefaultRunExecutor.EXECUTOR_ID -> AspireHostRunProfileState(executable, activeRuntime, environment)
            DefaultDebugExecutor.EXECUTOR_ID -> AspireHostDebugProfileState(executable, activeRuntime, environment)
            else -> throw CantRunException("Unable to execute Aspire host with $executorId executor")
        }
    }

    private suspend fun getDotNetExecutable(
        runnableProject: RunnableProject,
        projectOutput: ProjectOutput,
        launchProfile: LaunchProfile
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
        val environmentVariableValues = configureEnvironmentVariables(effectiveEnvs)

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
            File(runnableProject.projectFilePath),
            File(effectiveWorkingDirectory)
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

    private suspend fun configureEnvironmentVariables(envs: MutableMap<String, String>): EnvironmentVariableValues {
        val sessionHost = SessionHostManager2.getInstance(project).getOrStartSessionHost()

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
        val allowUnsecuredTransport = envs[ASPIRE_ALLOW_UNSECURED_TRANSPORT]?.equals("true", true) == true

        //Automatically set the `ASPIRE_ALLOW_UNSECURED_TRANSPORT` environment variable if the `http` protocol is used
        //see: https://learn.microsoft.com/en-us/dotnet/aspire/troubleshooting/allow-unsecure-transport
        if (isHttpUrl && !allowUnsecuredTransport) {
            envs[ASPIRE_ALLOW_UNSECURED_TRANSPORT] = "true"
        }

        val useHttp = isHttpUrl || allowUnsecuredTransport

        //Set the DOTNET_RESOURCE_SERVICE_ENDPOINT_URL environment variable if not specified
        if (!envs.containsKey(DOTNET_RESOURCE_SERVICE_ENDPOINT_URL)) {
            val resourceEndpointPort = NetUtils.findFreePort(47200)
            envs[DOTNET_RESOURCE_SERVICE_ENDPOINT_URL] =
                if (useHttp) "http://localhost:$resourceEndpointPort"
                else "https://localhost:$resourceEndpointPort"
        }

        val allowAnonymousDashboard = envs[DOTNET_DASHBOARD_UNSECURED_ALLOW_ANONYMOUS]?.equals("true", true) == true

        //Configure Dashboard frontend authentication
        //see: https://learn.microsoft.com/en-us/dotnet/aspire/fundamentals/dashboard/configuration#frontend-authentication
        var browserToken: String? = null
        if (!allowAnonymousDashboard) {
            browserToken = UUID.randomUUID().toString()
            envs[DOTNET_DASHBOARD_FRONTEND_BROWSERTOKEN] = browserToken
        }

        //Configure ApiKey for the Resource service
        //see: https://learn.microsoft.com/en-us/dotnet/aspire/fundamentals/dashboard/configuration#resources
        if (!allowAnonymousDashboard) {
            envs[DOTNET_DASHBOARD_RESOURCESERVICE_APIKEY] = UUID.randomUUID().toString()
        }

        //Configure Podman container runtime
        //see: https://learn.microsoft.com/en-us/dotnet/aspire/fundamentals/setup-tooling?tabs=linux&pivots=visual-studio#container-runtime
        if (parameters.usePodmanRuntime && !envs.containsKey(DOTNET_ASPIRE_CONTAINER_RUNTIME)) {
            envs[DOTNET_ASPIRE_CONTAINER_RUNTIME] = "podman"
        }

        return EnvironmentVariableValues(browserToken)
    }

    fun generateDcpInstancePrefix(): String {
        val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        return (1..5)
            .map { allowedChars.random() }
            .joinToString("")
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