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
import me.rafaelldi.aspire.run.AspireHostProgramRunner.Companion.DEBUG_SESSION_PORT
import me.rafaelldi.aspire.run.AspireHostProgramRunner.Companion.DEBUG_SESSION_TOKEN
import me.rafaelldi.aspire.run.AspireHostProgramRunner.Companion.DOTNET_DASHBOARD_OTLP_ENDPOINT_URL
import me.rafaelldi.aspire.run.AspireHostProgramRunner.Companion.DOTNET_RESOURCE_SERVICE_ENDPOINT_URL
import me.rafaelldi.aspire.settings.AspireSettings
import org.jetbrains.concurrency.await
import java.io.File
import java.util.*

class AspireHostExecutorFactory(
    private val project: Project,
    private val parameters: AspireHostConfigurationParameters
) : AsyncExecutorFactory {
    companion object {
        private const val ASPNETCORE_URLS = "ASPNETCORE_URLS"
        private const val ASPIRE_ALLOW_UNSECURED_TRANSPORT = "ASPIRE_ALLOW_UNSECURED_TRANSPORT"
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

        val debugSessionToken = UUID.randomUUID().toString()
        val debugSessionPort = NetUtils.findFreePort(67800)
        envs[DEBUG_SESSION_TOKEN] = debugSessionToken
        envs[DEBUG_SESSION_PORT] = "localhost:$debugSessionPort"

        if (!envs.containsKey(DOTNET_RESOURCE_SERVICE_ENDPOINT_URL)) {
            val resourceEndpointPort = NetUtils.findFreePort(77800)
            envs[DOTNET_RESOURCE_SERVICE_ENDPOINT_URL] = "https://localhost:$resourceEndpointPort"
        }

        val settings = AspireSettings.getInstance()
        if (settings.collectTelemetry && !envs.containsKey(DOTNET_DASHBOARD_OTLP_ENDPOINT_URL)) {
            val openTelemetryProtocolEndpointPort = NetUtils.findFreePort(87800)
            envs[DOTNET_DASHBOARD_OTLP_ENDPOINT_URL] = "https://localhost:$openTelemetryProtocolEndpointPort"
        }

        val urls = envs[ASPNETCORE_URLS]
        if (urls != null && !urls.contains("https") && !envs.containsKey(ASPIRE_ALLOW_UNSECURED_TRANSPORT)) {
            envs[ASPIRE_ALLOW_UNSECURED_TRANSPORT] = "true"
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
}