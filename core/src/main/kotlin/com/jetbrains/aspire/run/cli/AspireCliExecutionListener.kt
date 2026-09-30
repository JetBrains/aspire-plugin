package com.jetbrains.aspire.run.cli

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.run.AspireExecutionListener

internal class AspireCliExecutionListener(project: Project) : AspireExecutionListener(project) {
    override fun isValid(profile: RunProfile): Boolean = profile is AspireCliRunConfiguration

    override fun getProcessHandler(handler: ProcessHandler): ProcessHandler = handler
}
