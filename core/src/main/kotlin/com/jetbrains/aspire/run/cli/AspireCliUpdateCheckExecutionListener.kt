package com.jetbrains.aspire.run.cli

import com.intellij.execution.ExecutionListener
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.cli.AspireCliUpdateChecker

/**
 * Schedules an Aspire CLI update check whenever an [AspireCliRunConfiguration] is started.
 */
internal class AspireCliUpdateCheckExecutionListener(private val project: Project) : ExecutionListener {
    override fun processStarted(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
        if (env.runProfile !is AspireCliRunConfiguration) return
        AspireCliUpdateChecker.getInstance(project).scheduleCheck()
    }
}
