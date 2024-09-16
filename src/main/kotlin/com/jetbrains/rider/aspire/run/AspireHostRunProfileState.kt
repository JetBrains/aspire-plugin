package com.jetbrains.rider.aspire.run

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
import com.jetbrains.rider.runtime.DotNetRuntime
import kotlin.io.path.Path

class AspireHostRunProfileState(
    private val dotNetExecutable: DotNetExecutable,
    private val dotNetRuntime: DotNetRuntime,
    private val environment: ExecutionEnvironment
) : RunProfileState {

    val environmentVariables: Map<String, String> = dotNetExecutable.environmentVariables

    override fun execute(
        executor: Executor?,
        runner: ProgramRunner<*>
    ): ExecutionResult {
        dotNetExecutable.validate()

        val commandLine = dotNetExecutable.createRunCommandLine(dotNetRuntime)
        val originalExecutable = Path(commandLine.exePath)
        val processHandler = TerminalProcessHandler(
            environment.project,
            commandLine,
            commandLine.commandLineString,
            originalExecutable = originalExecutable
        )
        val console = createConsole(
            ConsoleKind.Normal,
            processHandler,
            environment.project
        )

        dotNetExecutable.onBeforeProcessStarted(environment, environment.runProfile, processHandler)

        return DefaultExecutionResult(console, processHandler)
    }
}