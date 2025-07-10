package com.jetbrains.rider.aspire.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.jetbrains.rider.aspire.projectTemplates.AspireProjectTemplateService

internal class AddAspireSupportAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        AspireProjectTemplateService.getInstance(project).createHotProjectFromTemplate()
    }

    override fun update(e: AnActionEvent) {

    }
}