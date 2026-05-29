package com.jetbrains.aspire.rider.run.states

import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.jetbrains.aspire.AspireService
import com.jetbrains.aspire.rider.run.checkAndNotifyDevCertificate
import com.jetbrains.aspire.rider.run.connectExecutionHandlerAndLifetime
import com.jetbrains.aspire.rider.run.setUpAspireHostModelAndSaveRunConfig
import com.jetbrains.rider.run.ConsoleKind
import com.jetbrains.rider.run.TerminalProcessHandler
import com.jetbrains.rider.run.configurations.RiderAsyncRunProfileState
import com.jetbrains.rider.run.createConsole
import com.jetbrains.rider.run.createRunCommandLineBlocking
import com.jetbrains.rider.runtime.DotNetExecutable
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntime
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.Path

class AspireHostRunProfileState(
    private val dotnetExecutable: DotNetExecutable,
    private val dotnetRuntime: DotNetCoreRuntime,
    private val environment: ExecutionEnvironment
) : RiderAsyncRunProfileState, RunProfileState, AspireHostProfileState {

    override val environmentVariables: Map<String, String> = dotnetExecutable.environmentVariables

    private val containerRuntimeNotificationCount = AtomicInteger()

    override suspend fun executeAsync(
        executor: Executor,
        runner: ProgramRunner<*>
    ): ExecutionResult {
        val aspireHostProcessHandlerLifetime = AspireService
            .getInstance(environment.project)
            .lifetime
            .createNested()

        checkAndNotifyDevCertificate(this, environment.project)

        setUpAspireHostModelAndSaveRunConfig(environment, this, aspireHostProcessHandlerLifetime)

        val executionResult = execute()

        connectExecutionHandlerAndLifetime(executionResult, aspireHostProcessHandlerLifetime)

        return executionResult
    }

    private fun execute(): ExecutionResult {
        dotnetExecutable.validate()

        val commandLine = dotnetExecutable.createRunCommandLineBlocking(dotnetRuntime)
        val originalExecutable = Path(commandLine.exePath)
        val processHandler = TerminalProcessHandler(
            environment.project,
            commandLine,
            commandLine.commandLineString,
            originalExecutable = originalExecutable
        )
        processHandler.addStoppedContainerRuntimeProcessListener(
            containerRuntimeNotificationCount,
            environment.project
        )
        val console = createConsole(
            ConsoleKind.Normal,
            processHandler,
            environment.project
        )

        dotnetExecutable.onBeforeProcessStarted(environment, environment.runProfile, processHandler)

        return DefaultExecutionResult(console, processHandler)
    }

    override fun execute(
        executor: Executor?,
        runner: ProgramRunner<*>
    ): ExecutionResult? {
        error("Use the async method instead")
    }
}