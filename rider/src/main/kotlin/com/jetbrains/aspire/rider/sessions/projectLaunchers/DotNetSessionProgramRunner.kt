package com.jetbrains.aspire.rider.sessions.projectLaunchers

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.executors.DefaultRunExecutor
import com.jetbrains.rider.debugger.DotNetProgramRunner

class DotNetSessionProgramRunner : DotNetProgramRunner() {
    companion object {
        const val ID = "aspire.dotnet.session.runner"
    }

    override fun getRunnerId() = ID

    override fun canRun(executorId: String, runConfiguration: RunProfile) =
        executorId == DefaultRunExecutor.EXECUTOR_ID && runConfiguration is DotNetSessionProfile && !runConfiguration.isDebugMode
}