@file:OptIn(ExperimentalCoroutinesApi::class)

package com.jetbrains.rider.aspire.sessions.projectLaunchers

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Key
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.debugger.DebuggerHelperHost
import com.jetbrains.rider.debugger.DebuggerWorkerProcessHandler
import com.jetbrains.rider.model.debuggerWorker.DebuggerWorkerModel
import com.jetbrains.rider.run.TerminalProcessHandler
import com.jetbrains.rider.run.dotNetCore.DotNetCoreDebugProfile
import com.jetbrains.rider.runtime.DotNetExecutable
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntime
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * Represents a debug profile state for executing a [DotNetExecutable] from an Aspire session request.
 *
 * The provided [sessionProcessEventListener] will be attached to the created [DebuggerWorkerProcessHandler].
 *
 * The created [DebuggerWorkerProcessHandler] will be connected to the [sessionProcessLifetime],
 * so that the debugger worker process will be killed on the lifetime termination.
 */
open class DotNetExecutableSessionDebugProfileState(
    private val sessionId: String,
    dotnetExecutable: DotNetExecutable,
    dotnetRuntime: DotNetCoreRuntime,
    environment: ExecutionEnvironment,
    private val sessionProcessEventListener: ProcessListener,
    private val sessionProcessLifetime: Lifetime
) : DotNetCoreDebugProfile(
    dotnetRuntime,
    dotnetExecutable,
    environment,
    dotnetRuntime.cliExePath
) {
    companion object {
        private val LOG = logger<DotNetExecutableSessionDebugProfileState>()
    }

    override suspend fun createWorkerRunInfo(
        lifetime: Lifetime,
        helper: DebuggerHelperHost,
        port: Int
    ) = createWorkerRunInfoForLauncherInfo(
        consoleKind,
        port,
        getLauncherInfo(lifetime, helper),
        dotNetExecutable.executableType,
        dotNetExecutable.usePty
    )

    override suspend fun startDebuggerWorker(
        workerCmd: GeneralCommandLine,
        protocolModel: DebuggerWorkerModel,
        protocolServerPort: Int,
        projectLifetime: Lifetime
    ): DebuggerWorkerProcessHandler {
        val presentableCommandLine = createPresentableCommandLine()
        val processHandler = object : TerminalProcessHandler(
            project,
            workerCmd,
            presentableCommandLine,
            false
        ) {
            override fun notifyTextAvailable(text: String, outputType: Key<*>) {
                val modifiedText = text.lineSequence().joinToString("\r\n")
                super.notifyTextAvailable(modifiedText, outputType)
            }
        }

        val debuggerWorkerProcessHandler = DebuggerWorkerProcessHandler(
            processHandler,
            protocolModel,
            attached,
            workerCmd.commandLineString,
            projectLifetime
        )

        sessionProcessLifetime.onTerminationIfAlive {
            if (!debuggerWorkerProcessHandler.isProcessTerminating && !debuggerWorkerProcessHandler.isProcessTerminated) {
                LOG.trace("Killing debugger worker session process handler (id: $sessionId)")
                debuggerWorkerProcessHandler.destroyProcess()
            }
        }

        debuggerWorkerProcessHandler.addProcessListener(sessionProcessEventListener)

        return debuggerWorkerProcessHandler
    }
}