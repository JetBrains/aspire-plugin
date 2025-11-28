package com.jetbrains.aspire.rider.sessions.wasmHost

import com.intellij.execution.CantRunException
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.xdebugger.XDebugSession
import com.jetbrains.rd.framework.IProtocol
import com.jetbrains.rd.framework.Protocol
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rider.debugger.DebuggerHelperHost
import com.jetbrains.rider.debugger.DebuggerWorkerProcessHandler
import com.jetbrains.rider.debugger.DotNetDebugRunner
import com.jetbrains.rider.debugger.editAndContinue.web.BrowserRefreshAgentManager
import com.jetbrains.rider.debugger.wasm.BrowserHubManager
import com.jetbrains.rider.model.debuggerWorker.DebuggerWorkerModel
import com.jetbrains.rider.model.debuggerWorker.DotNetDebuggerSessionModel
import com.jetbrains.rider.run.IDotNetDebugProfileState

internal class WasmHostProjectSessionDebugProgramRunner : DotNetDebugRunner() {
    companion object {
        const val ID = "aspire.wasm.project.session.debug.runner"
    }

    override fun getRunnerId() = ID

    override fun canRun(executorId: String, runConfiguration: RunProfile) =
        executorId == DefaultDebugExecutor.EXECUTOR_ID && runConfiguration is WasmHostProjectSessionDebugProfile

    override suspend fun initDebuggerSession(
        helper: DebuggerHelperHost,
        processLifetimeDefinition: LifetimeDefinition,
        protocol: Protocol,
        state: IDotNetDebugProfileState,
        environment: ExecutionEnvironment
    ): RunContentDescriptor {
        val wasmProfileState = state as? WasmHostProjectSessionDebugProfileState
            ?: throw CantRunException("State profile is not supported")

        wasmProfileState.browserRefreshHost =
            BrowserRefreshAgentManager
                .getInstance(environment.project)
                .startHost(
                    wasmProfileState.dotNetExecutable.projectTfm,
                    wasmProfileState.dotNetExecutable.environmentVariables,
                    processLifetimeDefinition.lifetime
                )

        wasmProfileState.browserHub =
            BrowserHubManager
                .getInstance(environment.project)
                .start(wasmProfileState.dotNetExecutable.environmentVariables, wasmProfileState.browserHubLifetimeDef)

        return super.initDebuggerSession(helper, processLifetimeDefinition, protocol, state, environment)
    }

    override suspend fun createAndStartSession(
        env: ExecutionEnvironment,
        state: IDotNetDebugProfileState,
        protocol: IProtocol,
        sessionLifetime: Lifetime,
        workerProcessHandler: DebuggerWorkerProcessHandler,
        sessionModel: DotNetDebuggerSessionModel,
        workerModel: DebuggerWorkerModel
    ): XDebugSession {
        val executionResult = state.execute(env.executor, this, workerProcessHandler, sessionLifetime)
        val executionConsole = executionResult.executionConsole
        val processHandler = executionResult.processHandler

        val wasmProfileState = state as? WasmHostProjectSessionDebugProfileState
            ?: throw CantRunException("State profile is not supported")

        return createAndStartSession(
            executionConsole,
            env,
            sessionLifetime,
            processHandler,
            protocol,
            sessionModel,
            state.getDebuggerOutputEventsListener(),
            wasmProfileState.browserRefreshHost
        ) { xDebuggerManager, xDebugProcessStarter ->
            xDebuggerManager.startSession(env, xDebugProcessStarter)
        }
    }
}