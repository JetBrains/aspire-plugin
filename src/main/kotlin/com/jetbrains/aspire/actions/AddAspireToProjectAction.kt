package com.jetbrains.aspire.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.currentThreadCoroutineScope
import com.jetbrains.aspire.orchestration.AspireOrchestrationService
import com.jetbrains.aspire.orchestration.isAspireOrchestrationSupported
import com.jetbrains.rider.model.RdProjectDescriptor
import com.jetbrains.rider.projectView.actions.isProjectModelReady
import com.jetbrains.rider.projectView.workspace.getProjectModelEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AddAspireToProjectAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        if (!project.isProjectModelReady()) return

        val projectModelEntity = e.dataContext.getProjectModelEntity() ?: return
        val descriptor = projectModelEntity.descriptor as? RdProjectDescriptor ?: return
        if (!descriptor.isDotNetCore) return

        currentThreadCoroutineScope().launch(Dispatchers.Default) {
            AspireOrchestrationService
                .getInstance(project)
                .addAspireOrchestration(listOf(projectModelEntity))
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null || !project.isProjectModelReady()) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val entity = e.dataContext.getProjectModelEntity()
        if (entity == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        e.presentation.isEnabledAndVisible = entity.isAspireOrchestrationSupported()
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}