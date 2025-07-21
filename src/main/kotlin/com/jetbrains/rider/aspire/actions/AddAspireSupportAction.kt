package com.jetbrains.rider.aspire.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.progress.currentThreadCoroutineScope
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.jetbrains.rider.aspire.projectGeneration.AspireOrchestrationSupportService
import com.jetbrains.rider.aspire.util.isAspireHostProject
import com.jetbrains.rider.aspire.util.isAspireSharedProject
import com.jetbrains.rider.projectView.workspace.findProjects
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal class AddAspireSupportAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        currentThreadCoroutineScope().launch(Dispatchers.Default) {
            val allProjects = project.serviceAsync<WorkspaceModel>().findProjects()
                .filter { !it.isAspireHostProject() && !it.isAspireSharedProject() }
            AspireOrchestrationSupportService
                .getInstance(project)
                .addAspireOrchestrationSupport(allProjects)
        }
    }

    override fun update(e: AnActionEvent) {

    }
}