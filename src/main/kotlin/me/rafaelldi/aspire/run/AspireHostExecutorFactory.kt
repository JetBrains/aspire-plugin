package me.rafaelldi.aspire.run

import com.intellij.execution.CantRunException
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.util.execution.ParametersListUtil
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.model.RunnableProject
import com.jetbrains.rider.model.runnableProjectsModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.run.configurations.AsyncExecutorFactory
import com.jetbrains.rider.run.configurations.launchSettings.LaunchSettingsJson
import com.jetbrains.rider.run.configurations.launchSettings.LaunchSettingsJsonService
import com.jetbrains.rider.run.configurations.project.DotNetProjectConfigurationParameters
import com.jetbrains.rider.run.environment.ExecutableParameterProcessor
import com.jetbrains.rider.run.environment.ExecutableRunParameters
import com.jetbrains.rider.run.environment.ProjectProcessOptions
import com.jetbrains.rider.runtime.DotNetExecutable
import com.jetbrains.rider.runtime.RiderDotNetActiveRuntimeHost
import com.jetbrains.rider.util.NetUtils
import me.rafaelldi.aspire.util.ASPIRE_ALLOW_UNSECURED_TRANSPORT
import me.rafaelldi.aspire.util.ASPNETCORE_URLS
import me.rafaelldi.aspire.util.DEBUG_SESSION_PORT
import me.rafaelldi.aspire.util.DEBUG_SESSION_TOKEN
import me.rafaelldi.aspire.util.DOTNET_DASHBOARD_FRONTEND_BROWSERTOKEN
import me.rafaelldi.aspire.util.DOTNET_DASHBOARD_RESOURCESERVICE_APIKEY
import me.rafaelldi.aspire.util.DOTNET_DASHBOARD_UNSECURED_ALLOW_ANONYMOUS
import me.rafaelldi.aspire.util.DOTNET_RESOURCE_SERVICE_ENDPOINT_URL
import org.jetbrains.concurrency.await
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

        val profile = getLaunchSettingsProfile(runnableProject)
            ?: throw CantRunException("Profile ${parameters.profileName} not found")

        val executable = getDotNetExecutable(runnableProject, profile)

        return when (executorId) {
            DefaultDebugExecutor.EXECUTOR_ID -> AspireHostRunProfileState(executable, activeRuntime, environment)
            DefaultRunExecutor.EXECUTOR_ID -> AspireHostRunProfileState(executable, activeRuntime, environment)
            else -> throw CantRunException("Unable to execute Aspire host with $executorId executor")
        }
    }

    private suspend fun getDotNetExecutable(
        runnableProject: RunnableProject,
        profile: LaunchSettingsJson.Profile
    ): DotNetExecutable {
        val projectOutput = runnableProject.projectOutputs.firstOrNull()
            ?: throw CantRunException("Unable to find project output")

        val commandLineArguments =
            if (projectOutput.defaultArguments.isEmpty()) profile.commandLineArgs
            else ParametersListUtil.join(projectOutput.defaultArguments) + " " + profile.commandLineArgs.orEmpty()

        val envs = parameters.envs.toMutableMap()
        val environmentVariableValues = configureEnvironmentVariables(envs)

        if (environmentVariableValues.browserToken != null) {
            val url = URI(parameters.startBrowserParameters.url)
            val updatedUrl = URI(
                url.scheme,
                null,
                url.host,
                url.port,
                "/login",
                "t=${environmentVariableValues.browserToken}",
                null
            )
            parameters.startBrowserParameters.url = updatedUrl.toString()
        }

        val processOptions = ProjectProcessOptions(
            File(runnableProject.projectFilePath),
            File(projectOutput.workingDirectory)
        )
        val runParameters = ExecutableRunParameters(
            projectOutput.exePath,
            projectOutput.workingDirectory,
            commandLineArguments,
            envs,
            true,
            projectOutput.tfm
        )

        val params = ExecutableParameterProcessor
            .getInstance(project)
            .processEnvironment(runParameters, processOptions)
            .await()

        return DotNetExecutable(
            params.executablePath ?: projectOutput.exePath,
            params.tfm ?: projectOutput.tfm,
            params.workingDirectoryPath ?: projectOutput.workingDirectory,
            params.commandLineArgumentString ?: ParametersListUtil.join(projectOutput.defaultArguments),
            false,
            false,
            params.environmentVariables,
            true,
            parameters.startBrowserAction,
            null,
            "",
            true
        )
    }

    private fun configureEnvironmentVariables(envs: MutableMap<String, String>): EnvironmentVariableValues {
        //Switch DCP to the IDE mode
        //see: https://github.com/dotnet/aspire/blob/main/docs/specs/IDE-execution.md#enabling-ide-execution
        val debugSessionToken = UUID.randomUUID().toString()
        val debugSessionPort = NetUtils.findFreePort(47100)
        envs[DEBUG_SESSION_TOKEN] = debugSessionToken
        envs[DEBUG_SESSION_PORT] = "localhost:$debugSessionPort"

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

        return EnvironmentVariableValues(browserToken)
    }

    private suspend fun getLaunchSettingsProfile(runnableProject: RunnableProject): LaunchSettingsJson.Profile? {
        val launchSettings = readAction {
            LaunchSettingsJsonService.loadLaunchSettings(runnableProject)
        }
        return launchSettings?.profiles?.get(parameters.profileName)
    }

    private data class EnvironmentVariableValues(
        val browserToken: String?
    )
}