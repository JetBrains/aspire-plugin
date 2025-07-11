package com.jetbrains.rider.aspire.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.currentThreadCoroutineScope
import com.intellij.openapi.project.DumbAwareAction
import com.jetbrains.rider.aspire.projectTemplates.AspireProjectTemplateService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal class AddAspireSupportAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        currentThreadCoroutineScope().launch(Dispatchers.Default) {
            AspireProjectTemplateService.getInstance(project).createHotProjectFromTemplate()
        }
    }

    override fun update(e: AnActionEvent) {

    }
}