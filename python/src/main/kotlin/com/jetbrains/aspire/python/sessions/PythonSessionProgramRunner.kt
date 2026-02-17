package com.jetbrains.aspire.python.sessions

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.GenericProgramRunner

class PythonSessionProgramRunner : GenericProgramRunner<RunnerSettings>() {
    companion object {
        const val ID = "aspire.python.session.runner"
    }

    override fun getRunnerId(): String = ID

    override fun canRun(executorId: String, profile: RunProfile): Boolean =
        executorId == DefaultRunExecutor.EXECUTOR_ID && profile is PythonSessionRunProfile
}
