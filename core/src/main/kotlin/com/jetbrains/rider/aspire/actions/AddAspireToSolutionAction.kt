package com.jetbrains.rider.aspire.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.currentThreadCoroutineScope
import com.jetbrains.rider.aspire.orchestration.AspireOrchestrationService
import com.jetbrains.rider.projectView.actions.isProjectModelReady
import com.jetbrains.rider.projectView.isDirectorySolution
import com.jetbrains.rider.projectView.workspace.getProjectModelEntity
import com.jetbrains.rider.projectView.workspace.isSolution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AddAspireToSolutionAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        if (!project.isProjectModelReady()) return

        val projectModelEntity = e.dataContext.getProjectModelEntity() ?: return
        if (!projectModelEntity.isSolution()) return

        currentThreadCoroutineScope().launch(Dispatchers.Default) {
            AspireOrchestrationService
                .getInstance(project)
                .addAspireOrchestration()
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null || project.isDirectorySolution || !project.isProjectModelReady()) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val projectModelEntity = e.dataContext.getProjectModelEntity()

        e.presentation.isEnabledAndVisible = projectModelEntity?.isSolution() == true
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}