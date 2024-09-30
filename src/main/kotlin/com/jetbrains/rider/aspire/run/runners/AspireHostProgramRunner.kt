package com.jetbrains.rider.aspire.run.runners

import com.intellij.execution.CantRunException
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.showRunContent
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.aspire.AspireService
import com.jetbrains.rider.aspire.run.AspireHostConfig
import com.jetbrains.rider.aspire.run.AspireHostConfiguration
import com.jetbrains.rider.aspire.run.states.AspireHostRunProfileState
import com.jetbrains.rider.debugger.DotNetProgramRunner

class AspireHostProgramRunner : DotNetProgramRunner() {
    companion object {
        private const val RUNNER_ID = "aspire-runner"

        private val LOG = logger<AspireHostProgramRunner>()
    }

    override fun getRunnerId() = RUNNER_ID

    override fun canRun(executorId: String, runConfiguration: RunProfile) =
        executorId == DefaultRunExecutor.EXECUTOR_ID &&
                runConfiguration is AspireHostConfiguration

    override suspend fun executeAsync(
        environment: ExecutionEnvironment,
        state: RunProfileState
    ): RunContentDescriptor? {
        LOG.info("Executing Aspire run profile state")

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

        startSessionHostAndSubscribe(config, environment.project)

        val executionResult = state.execute(environment.executor, this)
        if (executionResult == null) {
            LOG.warn("Unable to start Aspire run profile state")
            return null
        }

        connectExecutionHandlerAndLifetime(executionResult, aspireHostLifetimeDefinition)

        return showRunContent(executionResult, environment)
    }

    private fun createConfig(
        environment: ExecutionEnvironment,
        state: RunProfileState,
        aspireHostLifetime: Lifetime
    ): AspireHostConfig {
        val aspireHostProfileState = state as? AspireHostRunProfileState
            ?: throw CantRunException("Unable to execute RunProfileState: $state")

        return createAspireHostConfig(environment, aspireHostProfileState, aspireHostLifetime)
    }
}