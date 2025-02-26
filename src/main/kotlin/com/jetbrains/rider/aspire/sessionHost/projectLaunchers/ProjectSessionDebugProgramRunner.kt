package com.jetbrains.rider.aspire.sessionHost.projectLaunchers

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import com.jetbrains.rider.debugger.DotNetDebugRunner
import com.jetbrains.rider.run.configurations.RequiresPreparationRunProfileState

class ProjectSessionDebugProgramRunner : DotNetDebugRunner() {
    companion object {
        const val ID = "aspire.project.session.debug.runner"
    }

    override fun getRunnerId() = ID

    override fun canRun(executorId: String, runProfile: RunProfile) =
        executorId == DefaultDebugExecutor.EXECUTOR_ID && runProfile is ProjectSessionProfile && runProfile.isDebugMode

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