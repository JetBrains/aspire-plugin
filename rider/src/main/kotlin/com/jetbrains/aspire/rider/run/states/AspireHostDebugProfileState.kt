@file:OptIn(ExperimentalCoroutinesApi::class)

package com.jetbrains.aspire.rider.run.states

import com.intellij.execution.ExecutionResult
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import com.jetbrains.aspire.AspireService
import com.jetbrains.aspire.rider.run.runners.connectExecutionHandlerAndLifetime
import com.jetbrains.aspire.rider.run.runners.setUpAspireHostModelAndSaveRunConfig
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.debugger.DebuggerHelperHost
import com.jetbrains.rider.debugger.DebuggerWorkerProcessHandler
import com.jetbrains.rider.run.WorkerRunInfo
import com.jetbrains.rider.run.dotNetCore.DotNetCoreDebugProfile
import com.jetbrains.rider.runtime.DotNetExecutable
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.util.concurrent.atomic.AtomicInteger

class AspireHostDebugProfileState(
    dotnetExecutable: DotNetExecutable,
    dotnetRuntime: DotNetCoreRuntime,
    environment: ExecutionEnvironment
) : DotNetCoreDebugProfile(
    dotnetRuntime,
    dotnetExecutable,
    environment,
    dotnetRuntime.cliExePath
), AspireHostProfileState {

    override val environmentVariables: Map<String, String> = dotnetExecutable.environmentVariables

    private val containerRuntimeNotificationCount = AtomicInteger()

    override suspend fun createWorkerRunInfo(
        lifetime: Lifetime,
        helper: DebuggerHelperHost,
        port: Int
    ): WorkerRunInfo {
        return super.createWorkerRunInfo(lifetime, helper, port).apply {
            addProcessListener(
                createStoppedContainerRuntimeProcessListener(
                    containerRuntimeNotificationCount,
                    executionEnvironment.project
                )
            )
        }
    }

    override suspend fun execute(
        workerConsole: ConsoleView,
        workerProcessHandler: DebuggerWorkerProcessHandler,
        lifetime: Lifetime
    ): ExecutionResult {
        val aspireHostProcessHandlerLifetime = AspireService
            .getInstance(executionEnvironment.project)
            .lifetime
            .createNested()

        setUpAspireHostModelAndSaveRunConfig(executionEnvironment, this, aspireHostProcessHandlerLifetime)

        val executionResult = super.execute(workerConsole, workerProcessHandler, lifetime)

        connectExecutionHandlerAndLifetime(executionResult, aspireHostProcessHandlerLifetime)

        return executionResult
    }
}