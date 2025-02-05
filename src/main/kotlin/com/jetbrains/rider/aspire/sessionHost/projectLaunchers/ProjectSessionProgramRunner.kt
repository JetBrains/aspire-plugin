package com.jetbrains.rider.aspire.sessionHost.projectLaunchers

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.executors.DefaultRunExecutor
import com.jetbrains.rider.debugger.DotNetProgramRunner

class ProjectSessionProgramRunner : DotNetProgramRunner() {
    companion object {
        const val ID = "aspire.project.session.runner"
    }

    override fun getRunnerId() = ID

    override fun canRun(executorId: String, runProfile: RunProfile) =
        executorId == DefaultRunExecutor.EXECUTOR_ID && runProfile is ProjectSessionProfile
}