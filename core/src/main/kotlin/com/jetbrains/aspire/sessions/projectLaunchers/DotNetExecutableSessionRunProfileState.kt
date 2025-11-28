@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.sessions.projectLaunchers

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

/**
 * Represents a run profile state for executing a [DotNetExecutable] from an Aspire session request.
 *
 * The provided [sessionProcessEventListener] will be attached to the created [TerminalProcessHandler].
 *
 * The created [TerminalProcessHandler] will be connected to the [sessionProcessLifetime],
 * so that the process will be killed on the lifetime termination.
 */
class DotNetExecutableSessionRunProfileState(
    private val sessionId: String,
    private val dotnetExecutable: DotNetExecutable,
    private val dotnetRuntime: DotNetCoreRuntime,
    private val environment: ExecutionEnvironment,
    private val sessionProcessEventListener: ProcessListener,
    private val sessionProcessLifetime: Lifetime
) : RunProfileState {
    companion object {
        private val LOG = logger<DotNetExecutableSessionRunProfileState>()
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
            init {
                @Suppress("removal", "DEPRECATION")
                // It's said that the graceful termination with WinP is enabled by default,
                // but fo Rider it's not enabled, so we have to use this flag
                // TODO: Ask the platform to provide a way for opt-in WinP termination
                setShouldKillProcessSoftlyWithWinP(true)
            }

            override fun notifyTextAvailable(text: String, outputType: Key<*>) {
                val modifiedText = text.lineSequence().joinToString("\r\n")
                super.notifyTextAvailable(modifiedText, outputType)
            }
        }

        sessionProcessLifetime.onTerminationIfAlive {
            if (!processHandler.isProcessTerminating && !processHandler.isProcessTerminated) {
                LOG.trace("Killing run session process handler (id: $sessionId)")
                processHandler.destroyProcess()
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