package com.jetbrains.aspire.diagram.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.actions.dashboard.host.AspireHostBaseAction
import com.jetbrains.aspire.dashboard.AspireAppHostViewModel
import com.jetbrains.aspire.diagram.graph.ResourceGraphService
import com.jetbrains.aspire.worker.AspireAppHost

class ShowResourceGraphAction : AspireHostBaseAction() {
    override fun performAction(appHost: AspireAppHost, project: Project) {
        ResourceGraphService.getInstance(project).showResourceGraph(appHost)
    }

    override fun updateAction(event: AnActionEvent, appHostVm: AspireAppHostViewModel) {
        event.presentation.isEnabledAndVisible = appHostVm.isActive
    }
}