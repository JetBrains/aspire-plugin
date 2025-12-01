package com.jetbrains.aspire.rider.sessions.projectLaunchers

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import com.jetbrains.rider.debugger.DotNetDebugRunner
import com.jetbrains.rider.run.configurations.RequiresPreparationRunProfileState

class DotNetSessionDebugProgramRunner : DotNetDebugRunner() {
    companion object {
        const val ID = "aspire.dotnet.session.debug.runner"
    }

    override fun getRunnerId() = ID

    override fun canRun(executorId: String, runConfiguration: RunProfile) =
        executorId == DefaultDebugExecutor.EXECUTOR_ID && runConfiguration is DotNetSessionProfile && runConfiguration.isDebugMode

    override suspend fun executeAsync(
        environment: ExecutionEnvironment,
        state: RunProfileState
    ): RunContentDescriptor? {
        if (state is RequiresPreparationRunProfileState) {
            state.prepareExecution(environment)
        }

        return super.executeAsync(environment, state)
    }
}