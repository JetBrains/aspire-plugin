@file:Suppress("UnstableApiUsage")

package com.jetbrains.rider.aspire.sessionHost.projectLaunchers

import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Key
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.run.ConsoleKind
import com.jetbrains.rider.run.TerminalProcessHandler
import com.jetbrains.rider.run.createConsole
import com.jetbrains.rider.run.createRunCommandLine
import com.jetbrains.rider.runtime.DotNetExecutable
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntime
import kotlin.io.path.Path

class ProjectSessionRunProfileState(
    private val sessionId: String,
    private val dotnetExecutable: DotNetExecutable,
    private val dotnetRuntime: DotNetCoreRuntime,
    private val environment: ExecutionEnvironment,
    private val sessionProcessEventListener: ProcessListener,
    private val sessionProcessLifetime: Lifetime
) : RunProfileState {
    companion object {
        private val LOG = logger<ProjectSessionRunProfileState>()
    }

    override fun execute(
        executor: Executor,
        runner: ProgramRunner<*>
    ): ExecutionResult {
        val commandLine = dotnetExecutable.createRunCommandLine(dotnetRuntime)
        val originalExecutable = Path(commandLine.exePath)
        val processHandler = object : TerminalProcessHandler(
            environment.project,
            commandLine,
            commandLine.commandLineString,
            originalExecutable = originalExecutable
        ) {
            override fun notifyTextAvailable(text: String, outputType: Key<*>) {
                val modifiedText = text.lineSequence().joinToString("\r\n")
                super.notifyTextAvailable(modifiedText, outputType)
            }
        }

        sessionProcessLifetime.onTerminationIfAlive {
            if (!processHandler.isProcessTerminating && !processHandler.isProcessTerminated) {
                LOG.trace("Killing run session process handler (id: $sessionId)")
                processHandler.killProcess()
            }
        }

        processHandler.addProcessListener(sessionProcessEventListener)

        val console = createConsole(
            ConsoleKind.Normal,
            processHandler,
            environment.project
        )

        dotnetExecutable.onBeforeProcessStarted(environment, environment.runProfile, processHandler)

        return DefaultExecutionResult(console, processHandler)
    }
}