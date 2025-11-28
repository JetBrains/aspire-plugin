package com.jetbrains.aspire.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.jetbrains.aspire.settings.AspireConfigurable

class AspireSettingsAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        ShowSettingsUtil.getInstance().showSettingsDialog(project, AspireConfigurable::class.java)
    }

    override fun update(event: AnActionEvent) {
        val project = event.project
        event.presentation.isEnabledAndVisible = project != null
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}