package com.jetbrains.rider.aspire.run.states

import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.jetbrains.rider.run.ConsoleKind
import com.jetbrains.rider.run.TerminalProcessHandler
import com.jetbrains.rider.run.createConsole
import com.jetbrains.rider.run.createRunCommandLine
import com.jetbrains.rider.runtime.DotNetExecutable
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntime
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.Path

class AspireHostRunProfileState(
    private val dotnetExecutable: DotNetExecutable,
    private val dotnetRuntime: DotNetCoreRuntime,
    private val environment: ExecutionEnvironment
) : RunProfileState, AspireHostProfileState {

    override val environmentVariables: Map<String, String> = dotnetExecutable.environmentVariables

    private val containerRuntimeNotificationCount = AtomicInteger()

    override fun execute(
        executor: Executor?,
        runner: ProgramRunner<*>
    ): ExecutionResult {
        dotnetExecutable.validate()

        val commandLine = dotnetExecutable.createRunCommandLine(dotnetRuntime)
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
}