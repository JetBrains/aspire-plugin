package me.rafaelldi.aspire.run

import com.intellij.execution.CantRunException
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project
import com.intellij.util.execution.ParametersListUtil
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.model.RunnableProject
import com.jetbrains.rider.model.runnableProjectsModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.run.configurations.AsyncExecutorFactory
import com.jetbrains.rider.run.configurations.project.DotNetProjectConfigurationParameters
import com.jetbrains.rider.run.environment.ExecutableParameterProcessor
import com.jetbrains.rider.run.environment.ExecutableRunParameters
import com.jetbrains.rider.run.environment.ProjectProcessOptions
import com.jetbrains.rider.runtime.DotNetExecutable
import com.jetbrains.rider.runtime.RiderDotNetActiveRuntimeHost
import com.jetbrains.rider.util.NetUtils
import me.rafaelldi.aspire.util.ASPIRE_ALLOW_UNSECURED_TRANSPORT
import me.rafaelldi.aspire.util.ASPNETCORE_URLS
import me.rafaelldi.aspire.util.BROWSER_TOKEN
import me.rafaelldi.aspire.util.DEBUG_SESSION_PORT
import me.rafaelldi.aspire.util.DEBUG_SESSION_TOKEN
import me.rafaelldi.aspire.util.DOTNET_DASHBOARD_FRONTEND_AUTHMODE
import me.rafaelldi.aspire.util.DOTNET_DASHBOARD_FRONTEND_BROWSERTOKEN
import me.rafaelldi.aspire.util.DOTNET_DASHBOARD_OTLP_ENDPOINT_URL
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

        val executable = getDotNetExecutable(runnableProject)

        return when (executorId) {
            DefaultDebugExecutor.EXECUTOR_ID -> activeRuntime.createRunState(executable, environment)
            DefaultRunExecutor.EXECUTOR_ID -> activeRuntime.createRunState(executable, environment)
            else -> throw CantRunException("Unable to execute Aspire host with $executorId executor")
        }
    }

    private suspend fun getDotNetExecutable(runnableProject: RunnableProject): DotNetExecutable {
        val projectOutput = runnableProject.projectOutputs.firstOrNull()
            ?: throw CantRunException("Unable to find project output")

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
            ParametersListUtil.join(projectOutput.defaultArguments),
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

        //Configure Dashboard frontend authentication
        //see: https://learn.microsoft.com/en-us/dotnet/aspire/fundamentals/dashboard/configuration#frontend-authentication
        val dashboardFrontendAuthMode = envs[DOTNET_DASHBOARD_FRONTEND_AUTHMODE]
        var browserToken: String? = null
        if (dashboardFrontendAuthMode == null || dashboardFrontendAuthMode.equals(BROWSER_TOKEN, true)) {
            browserToken = UUID.randomUUID().toString()
            envs[DOTNET_DASHBOARD_FRONTEND_AUTHMODE] = BROWSER_TOKEN
            envs[DOTNET_DASHBOARD_FRONTEND_BROWSERTOKEN] = browserToken
        }

        val urls = envs[ASPNETCORE_URLS]
        //Check if the use of the `http` protocol is enabled
        val useHttp = (urls != null && !urls.contains("https")) || envs.containsKey(ASPIRE_ALLOW_UNSECURED_TRANSPORT)

        //Automatically set the `ASPIRE_ALLOW_UNSECURED_TRANSPORT` environment variable if the `http` protocol is used
        //see: https://learn.microsoft.com/en-us/dotnet/aspire/troubleshooting/allow-unsecure-transport
        if (useHttp && !envs.containsKey(ASPIRE_ALLOW_UNSECURED_TRANSPORT)) {
            envs[ASPIRE_ALLOW_UNSECURED_TRANSPORT] = "true"
        }

        //Set the DOTNET_RESOURCE_SERVICE_ENDPOINT_URL environment variable if not specified
        if (!envs.containsKey(DOTNET_RESOURCE_SERVICE_ENDPOINT_URL)) {
            val resourceEndpointPort = NetUtils.findFreePort(47200)
            envs[DOTNET_RESOURCE_SERVICE_ENDPOINT_URL] =
                if (useHttp) "http://localhost:$resourceEndpointPort"
                else "https://localhost:$resourceEndpointPort"
        }

        //Set the DOTNET_DASHBOARD_OTLP_ENDPOINT_URL environment variable if not specified
        if (!envs.containsKey(DOTNET_DASHBOARD_OTLP_ENDPOINT_URL)) {
            val openTelemetryProtocolEndpointPort = NetUtils.findFreePort(47300)
            envs[DOTNET_DASHBOARD_OTLP_ENDPOINT_URL] =
                if (useHttp) "http://localhost:$openTelemetryProtocolEndpointPort"
                else "https://localhost:$openTelemetryProtocolEndpointPort"
        }

        return EnvironmentVariableValues(browserToken)
    }

    private data class EnvironmentVariableValues(
        val browserToken: String?
    )
}