package com.jetbrains.aspire.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.jetbrains.aspire.cli.AspireCliUpdateChecker

internal class CheckAspireCliUpdatesAction : DumbAwareAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        AspireCliUpdateChecker.getInstance(project).scheduleCheck(bypassCache = true)
    }

    override fun update(event: AnActionEvent) {
        val project = event.project
        event.presentation.isEnabledAndVisible = project != null && !project.isDefault && !project.isDisposed
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
