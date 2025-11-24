package com.jetbrains.rider.aspire.run.file

import com.intellij.execution.CantRunException
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.aspire.run.AspireExecutorFactory
import com.jetbrains.rider.aspire.run.states.AspireHostRunProfileState
import com.jetbrains.rider.aspire.util.getStartBrowserAction
import com.jetbrains.rider.run.configurations.TerminalMode
import com.jetbrains.rider.runtime.DotNetExecutable
import com.jetbrains.rider.runtime.RiderDotNetActiveRuntimeHost
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntime

internal class AspireFileExecutorFactory(
    private val project: Project,
    private val parameters: AspireFileConfigurationParameters
) : AspireExecutorFactory(project, parameters) {
    override suspend fun create(
        executorId: String,
        environment: ExecutionEnvironment,
        lifetime: Lifetime
    ): RunProfileState {
        val activeRuntime = RiderDotNetActiveRuntimeHost.getInstance(project).dotNetCoreRuntime.value
            ?: throw CantRunException("Unable to find appropriate dotnet runtime")

        val executable = getDotNetExecutable(activeRuntime)

        return when (executorId) {
            DefaultRunExecutor.EXECUTOR_ID -> AspireHostRunProfileState(executable, activeRuntime, environment)
            else -> throw CantRunException("Unable to execute Aspire host with $executorId executor")
        }
    }

    private suspend fun getDotNetExecutable(
        activeRuntime: DotNetCoreRuntime
    ): DotNetExecutable {
        val effectiveArguments = buildString {
            append("--file ")
            append(parameters.filePath)
        }

        val effectiveEnvs = parameters.envs.toMutableMap()
        val environmentVariableValues = configureEnvironmentVariables(effectiveEnvs, activeRuntime)

        var effectiveUrl = parameters.startBrowserParameters.url
        if (environmentVariableValues.browserToken != null) {
            effectiveUrl = configureUrl(effectiveUrl, environmentVariableValues.browserToken)
        }

        val effectiveLaunchBrowser = parameters.startBrowserParameters.startAfterLaunch

        return DotNetExecutable(
            activeRuntime.cliExePath,
            null,
            parameters.workingDirectory,
            effectiveArguments,
            TerminalMode.Auto,
            effectiveEnvs,
            true,
            getStartBrowserAction(effectiveUrl, effectiveLaunchBrowser, parameters.startBrowserParameters),
            null,
            "",
            true
        )
    }
}