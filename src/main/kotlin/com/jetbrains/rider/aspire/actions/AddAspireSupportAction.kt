package com.jetbrains.rider.aspire.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.currentThreadCoroutineScope
import com.intellij.openapi.project.DumbAwareAction
import com.jetbrains.rider.aspire.projectTemplates.AspireOrchestrationSupportService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal class AddAspireSupportAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        currentThreadCoroutineScope().launch(Dispatchers.Default) {
            AspireOrchestrationSupportService.getInstance(project).addAspireOrchestrationSupport()
        }
    }

    override fun update(e: AnActionEvent) {

    }
}