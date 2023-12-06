package com.intellij.aspire.run

import com.intellij.aspire.sessionHost.AspireSessionHostService
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
import com.jetbrains.rider.runtime.DotNetExecutable
import com.jetbrains.rider.runtime.RiderDotNetActiveRuntimeHost
import com.jetbrains.rider.util.NetUtils
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
            AspireHostConfigurationType.isTypeApplicable(it.kind) && it.projectFilePath == parameters.projectFilePath
        } ?: throw CantRunException(DotNetProjectConfigurationParameters.PROJECT_NOT_SPECIFIED)

        val sessionId = UUID.randomUUID().toString()
        val isDebug = executorId == DefaultDebugExecutor.EXECUTOR_ID
        val aspNetPort = NetUtils.findFreePort(67800)

        val sessionHost = AspireSessionHostService.getInstance()
        val config = AspireSessionHostService.HostConfiguration(
            sessionId,
            runnableProject.name,
            isDebug,
            aspNetPort
        )
        sessionHost.startHost(project, config, lifetime)

        val executable = getDotNetExecutable(
            runnableProject,
            aspNetPort,
            sessionId
        )

        return when (executorId) {
            DefaultDebugExecutor.EXECUTOR_ID -> activeRuntime.createRunState(executable, environment)
            DefaultRunExecutor.EXECUTOR_ID -> activeRuntime.createRunState(executable, environment)
            else -> throw CantRunException("Unable to execute Aspire host with $executorId executor")
        }
    }

    private fun getDotNetExecutable(
        runnableProject: RunnableProject,
        port: Int,
        token: String
    ): DotNetExecutable {
        val projectOutput = runnableProject.projectOutputs.firstOrNull()
            ?: throw CantRunException("Unable to find project output")
        val envs = runnableProject.environmentVariables
            .associate { it.key to it.value }
            .toMutableMap()
        envs["DEBUG_SESSION_PORT"] = "localhost:$port"
        envs["DEBUG_SESSION_TOKEN"] = token

        return DotNetExecutable(
            projectOutput.exePath,
            projectOutput.tfm,
            projectOutput.workingDirectory,
            ParametersListUtil.join(projectOutput.defaultArguments),
            false,
            false,
            envs,
            true,
            parameters.startBrowserAction,
            null,
            "",
            true
        )
    }
}