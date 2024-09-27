package com.jetbrains.rider.aspire.run.runners

import com.intellij.execution.CantRunException
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.xdebugger.XDebugSession
import com.jetbrains.rd.framework.IProtocol
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.aspire.AspireService
import com.jetbrains.rider.aspire.run.AspireHostConfig
import com.jetbrains.rider.aspire.run.AspireHostConfiguration
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
                runConfiguration is AspireHostConfiguration

    override suspend fun createAndStartSession(
        environment: ExecutionEnvironment,
        state: IDotNetDebugProfileState,
        protocol: IProtocol,
        sessionLifetime: Lifetime,
        workerProcessHandler: DebuggerWorkerProcessHandler,
        sessionModel: DotNetDebuggerSessionModel,
        workerModel: DebuggerWorkerModel
    ): XDebugSession {
        LOG.info("Creating Aspire debug session")

        val aspireHostLifetimeDefinition = AspireService
            .getInstance(environment.project)
            .lifetime
            .createNested()

        val config = createConfig(environment, state, aspireHostLifetimeDefinition.lifetime)

        LOG.trace { "Aspire session host config: $config" }

        saveRunConfiguration(
            environment.project,
            config.aspireHostProjectPath,
            config.name,
            aspireHostLifetimeDefinition
        )

        startSessionHostAndSubscribe(
            environment.project,
            config,
            aspireHostLifetimeDefinition
        )

        val executionResult = state.execute(environment.executor, this, workerProcessHandler, sessionLifetime)

        connectExecutionHandlerAndLifetime(executionResult, aspireHostLifetimeDefinition)

        return com.jetbrains.rider.debugger.createAndStartSession(
            executionResult.executionConsole,
            environment,
            sessionLifetime,
            executionResult.processHandler,
            protocol,
            sessionModel,
            state.getDebuggerOutputEventsListener()
        ) { xDebuggerManager, xDebugProcessStarter ->
            xDebuggerManager.startSession(environment, xDebugProcessStarter)
        }
    }

    private fun createConfig(
        environment: ExecutionEnvironment,
        state: RunProfileState,
        aspireHostLifetime: Lifetime
    ): AspireHostConfig {
        val aspireHostProfileState = state as? AspireHostDebugProfileState
            ?: throw CantRunException("Unable to execute RunProfileState: $state")

        return createAspireHostConfig(environment, aspireHostProfileState, aspireHostLifetime)
    }
}