package com.jetbrains.aspire.rider.run.runners

import com.intellij.execution.CantRunException
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.showRunContent
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.diagnostic.logger
import com.jetbrains.aspire.AspireService
import com.jetbrains.aspire.rider.run.file.AspireFileConfiguration
import com.jetbrains.aspire.rider.run.host.AspireHostConfiguration
import com.jetbrains.aspire.rider.run.states.AspireHostRunProfileState
import com.jetbrains.rider.debugger.DotNetProgramRunner

class AspireHostProgramRunner : DotNetProgramRunner() {
    companion object {
        private const val RUNNER_ID = "aspire-runner"

        private val LOG = logger<AspireHostProgramRunner>()
    }

    override fun getRunnerId() = RUNNER_ID

    override fun canRun(executorId: String, runConfiguration: RunProfile) =
        executorId == DefaultRunExecutor.EXECUTOR_ID &&
                (runConfiguration is AspireHostConfiguration || runConfiguration is AspireFileConfiguration)

    override suspend fun executeAsync(
        environment: ExecutionEnvironment,
        state: RunProfileState
    ): RunContentDescriptor? {
        LOG.info("Executing Aspire run profile state")

        if (state !is AspireHostRunProfileState) {
            throw CantRunException("Unable to execute RunProfileState: $state")
        }

        val aspireHostProcessHandlerLifetime = AspireService
            .getInstance(environment.project)
            .lifetime
            .createNested()

        setUpAspireHostModelAndSaveRunConfig(environment, state, aspireHostProcessHandlerLifetime)

        val executionResult = state.execute(environment.executor, this)

        connectExecutionHandlerAndLifetime(executionResult, aspireHostProcessHandlerLifetime)

        return showRunContent(executionResult, environment)
    }
}