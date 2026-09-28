package com.jetbrains.aspire.rider.run

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.project.Project
import com.jetbrains.rider.debugger.DebuggerWorkerProcessHandler

internal class AspireExecutionListener(project: Project) : com.jetbrains.aspire.run.AspireExecutionListener(project) {
    override fun isValid(profile: RunProfile): Boolean = profile is AspireRiderRunConfiguration

    override fun getProcessHandler(handler: ProcessHandler): ProcessHandler =
        if (handler is DebuggerWorkerProcessHandler) handler.debuggerWorkerRealHandler
        else handler
}
