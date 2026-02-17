package com.jetbrains.aspire.python.sessions

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.jetbrains.aspire.sessions.PythonSessionLaunchConfiguration
import com.jetbrains.python.icons.PythonIcons
import com.jetbrains.rd.util.lifetime.Lifetime
import kotlin.io.path.name

class PythonSessionRunProfile(
    val sessionId: String,
    val launchConfiguration: PythonSessionLaunchConfiguration,
    val sessionProcessEventListener: ProcessListener,
    val sessionProcessLifetime: Lifetime
) : RunProfile {
    override fun getName(): String = launchConfiguration.programPath.name

    override fun getIcon() = PythonIcons.Python.PythonClosed

    override fun getState(executor: com.intellij.execution.Executor, environment: ExecutionEnvironment): RunProfileState {
        return PythonSessionRunProfileState(
            sessionId,
            launchConfiguration,
            environment,
            sessionProcessEventListener,
            sessionProcessLifetime
        )
    }
}
