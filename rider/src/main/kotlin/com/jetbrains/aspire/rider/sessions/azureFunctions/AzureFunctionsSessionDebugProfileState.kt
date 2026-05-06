@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.rider.sessions.azureFunctions

import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.diagnostic.logger
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.azureFunctions.run.profileStates.AzureFunctionsIsolatedBaseDebugProfileState
import com.jetbrains.rider.debugger.DebuggerHelperHost
import com.jetbrains.rider.run.ConsoleKind
import com.jetbrains.rider.run.DebugProfileStateBase
import com.jetbrains.rider.run.configurations.shouldUsePty
import com.jetbrains.rider.run.dotNetCore.DotNetCoreAttachProfileState
import com.jetbrains.rider.run.kill
import com.jetbrains.rider.runtime.DotNetExecutable
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntime
import kotlin.collections.component1
import kotlin.collections.component2

/**
 * Represents a run profile state for debugging a [DotNetExecutable] created from Azure Functions project.
 *
 * Before the execution it launches the Azure Functions core tools and waits for the target process id.
 * After that it uses [DotNetCoreAttachProfileState] to attach to the process.
 */
internal class AzureFunctionsSessionDebugProfileState(
    private val sessionId: String,
    private val dotnetExecutable: DotNetExecutable,
    dotnetRuntime: DotNetCoreRuntime,
    executionEnvironment: ExecutionEnvironment,
    private val sessionProcessEventListener: ProcessListener,
    private val sessionProcessLifetime: Lifetime
) : AzureFunctionsIsolatedBaseDebugProfileState(dotnetExecutable, dotnetRuntime, executionEnvironment) {
    companion object {
        private val LOG = logger<AzureFunctionsSessionDebugProfileState>()
    }

    override val consoleKind = ConsoleKind.Normal

    override suspend fun prepareExecution(environment: ExecutionEnvironment) {
        val (executionResult, pid) = launchFunctionHostWaitingForDebugger(
            environment,
            sessionProcessEventListener,
            true
        ) ?: return

        functionHostExecutionResult = executionResult

        sessionProcessLifetime.onTerminationIfAlive {
            if (!executionResult.processHandler.isProcessTerminated && !executionResult.processHandler.isProcessTerminated) {
                LOG.trace("Killing Function session process handler (id: $sessionId)")
                executionResult.processHandler.kill()
            }
        }

        val targetProcess = getTargetProcess(pid) ?: return

        val processArchitecture = getPlatformArchitecture(sessionProcessLifetime, pid, environment.project)

        wrappedState = DotNetCoreAttachProfileState(
            targetProcess,
            environment,
            processArchitecture
        )
    }

    override suspend fun createWorkerRunInfo(
        lifetime: Lifetime,
        helper: DebuggerHelperHost,
        port: Int
    ) = DebugProfileStateBase.createWorkerRunInfoForLauncherInfo(
        consoleKind,
        port,
        getLauncherInfo(lifetime, helper),
        dotnetExecutable.executableType,
        dotnetExecutable.terminalMode.shouldUsePty() != false
    )
}