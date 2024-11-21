@file:OptIn(ExperimentalCoroutinesApi::class)

package com.jetbrains.rider.aspire.sessionHost.projectLaunchers

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.diagnostic.logger
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.debugger.DebuggerHelperHost
import com.jetbrains.rider.debugger.DebuggerWorkerProcessHandler
import com.jetbrains.rider.model.debuggerWorker.DebuggerWorkerModel
import com.jetbrains.rider.run.TerminalProcessHandler
import com.jetbrains.rider.run.dotNetCore.DotNetCoreDebugProfile
import com.jetbrains.rider.runtime.DotNetExecutable
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntime
import kotlinx.coroutines.ExperimentalCoroutinesApi

open class ProjectSessionDebugProfileState(
    private val sessionId: String,
    dotnetExecutable: DotNetExecutable,
    dotnetRuntime: DotNetCoreRuntime,
    environment: ExecutionEnvironment,
    private val sessionProcessEventListener: ProcessListener,
    private val sessionProcessTerminatedListener: ProcessListener,
    private val sessionProcessLifetime: Lifetime
) : DotNetCoreDebugProfile(
    dotnetRuntime,
    dotnetExecutable,
    environment,
    dotnetRuntime.cliExePath
) {
    companion object {
        private val LOG = logger<ProjectSessionDebugProfileState>()
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
        val processHandler = TerminalProcessHandler(project, workerCmd, createPresentableCommandLine(), false)
        val debuggerWorkerProcessHandler = DebuggerWorkerProcessHandler(
            processHandler,
            protocolModel,
            attached,
            workerCmd.commandLineString,
            projectLifetime
        )

        sessionProcessLifetime.onTermination {
            if (!debuggerWorkerProcessHandler.isProcessTerminating && !debuggerWorkerProcessHandler.isProcessTerminated) {
                LOG.trace("Killing debugger worker session process handler (id: $sessionId)")
                debuggerWorkerProcessHandler.killProcess()
            }
        }

        debuggerWorkerProcessHandler.debuggerWorkerRealHandler.addProcessListener(sessionProcessEventListener)
        debuggerWorkerProcessHandler.addProcessListener(sessionProcessTerminatedListener)

        return debuggerWorkerProcessHandler
    }
}