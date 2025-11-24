package com.jetbrains.rider.aspire.run.runners

import com.intellij.execution.CantRunException
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.diagnostic.logger
import com.intellij.xdebugger.XDebugSession
import com.jetbrains.rd.framework.IProtocol
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.aspire.AspireService
import com.jetbrains.rider.aspire.run.file.AspireFileConfiguration
import com.jetbrains.rider.aspire.run.host.AspireHostConfiguration
import com.jetbrains.rider.aspire.run.states.AspireHostDebugProfileState
import com.jetbrains.rider.debugger.DebuggerWorkerProcessHandler
import com.jetbrains.rider.debugger.DotNetDebugRunner
import com.jetbrains.rider.model.debuggerWorker.DebuggerWorkerModel
import com.jetbrains.rider.model.debuggerWorker.DotNetDebuggerSessionModel
import com.jetbrains.rider.run.IDotNetDebugProfileState

class AspireHostDebugProgramRunner : DotNetDebugRunner() {
    companion object {
        private const val RUNNER_ID = "aspire-debug-runner"

        private val LOG = logger<AspireHostDebugProgramRunner>()
    }

    override fun getRunnerId() = RUNNER_ID

    override fun canRun(executorId: String, runConfiguration: RunProfile) =
        executorId == DefaultDebugExecutor.EXECUTOR_ID &&
                (runConfiguration is AspireHostConfiguration || runConfiguration is AspireFileConfiguration)

    override suspend fun createAndStartSession(
        env: ExecutionEnvironment,
        state: IDotNetDebugProfileState,
        protocol: IProtocol,
        sessionLifetime: Lifetime,
        workerProcessHandler: DebuggerWorkerProcessHandler,
        sessionModel: DotNetDebuggerSessionModel,
        workerModel: DebuggerWorkerModel
    ): XDebugSession {
        LOG.info("Creating Aspire debug session")

        if (state !is AspireHostDebugProfileState) {
            throw CantRunException("Unable to execute RunProfileState: $state")
        }

        val aspireHostProcessHandlerLifetime = AspireService
            .getInstance(env.project)
            .lifetime
            .createNested()

        setUpAspireHostModelAndSaveRunConfig(env, state, aspireHostProcessHandlerLifetime)

        val executionResult = state.execute(env.executor, this, workerProcessHandler, sessionLifetime)

        connectExecutionHandlerAndLifetime(executionResult, aspireHostProcessHandlerLifetime)

        return com.jetbrains.rider.debugger.createAndStartSession(
            executionResult.executionConsole,
            env,
            sessionLifetime,
            executionResult.processHandler,
            protocol,
            sessionModel,
            state.getDebuggerOutputEventsListener(),
            false
        ) { xDebuggerManager, xDebugProcessStarter ->
            xDebuggerManager.startSession(env, xDebugProcessStarter)
        }
    }
}