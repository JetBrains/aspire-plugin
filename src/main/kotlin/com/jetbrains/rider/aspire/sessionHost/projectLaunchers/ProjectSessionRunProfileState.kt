@file:Suppress("UnstableApiUsage")

package com.jetbrains.rider.aspire.sessionHost.projectLaunchers

import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.debugger.DebuggerWorkerProcessHandler
import com.jetbrains.rider.run.*
import com.jetbrains.rider.runtime.DotNetExecutable
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntime
import kotlin.io.path.Path

class ProjectSessionRunProfileState(
    private val sessionId: String,
    private val dotnetExecutable: DotNetExecutable,
    private val dotnetRuntime: DotNetCoreRuntime,
    private val environment: ExecutionEnvironment,
    private val sessionProcessEventListener: ProcessListener,
    private val sessionProcessTerminatedListener: ProcessListener,
    private val sessionProcessLifetime: Lifetime
) : IDotNetProfileState {
    companion object {
        private val LOG = logger<ProjectSessionRunProfileState>()
    }

    override fun execute(
        executor: Executor,
        runner: ProgramRunner<*>
    ): ExecutionResult {
        val commandLine = dotnetExecutable.createRunCommandLine(dotnetRuntime)
        val originalExecutable = Path(commandLine.exePath)

        val processListeners = PatchCommandLineExtension.EP_NAME.getExtensions(environment.project).mapNotNull {
            LOG.runAndLogException {
                it.patchRunCommandLine(commandLine, dotnetRuntime, environment.project)
            }
        }

        val processHandler = TerminalProcessHandler(
            environment.project,
            commandLine,
            commandLine.commandLineString,
            originalExecutable = originalExecutable
        )

        sessionProcessLifetime.onTermination {
            if (!processHandler.isProcessTerminating && !processHandler.isProcessTerminated) {
                LOG.trace("Killing run session process handler (id: $sessionId)")
                processHandler.killProcess()
            }
        }

        processHandler.pid()?.let {
            environment.putUserData(DebuggerWorkerProcessHandler.PID_KEY, it.toInt())
        }

        processHandler.addProcessListener(sessionProcessEventListener)
        processHandler.addProcessListener(sessionProcessTerminatedListener)

        processListeners.forEach { processHandler.addProcessListener(it) }

        val console = createConsole(
            ConsoleKind.Normal,
            processHandler,
            environment.project
        )

        dotnetExecutable.onBeforeProcessStarted(environment, environment.runProfile, processHandler)

        return return DefaultExecutionResult(console, processHandler)
    }
}