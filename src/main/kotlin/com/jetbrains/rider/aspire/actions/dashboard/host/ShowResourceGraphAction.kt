package com.jetbrains.rider.aspire.actions.dashboard.host

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.jetbrains.rider.aspire.graph.ResourceGraphService
import com.jetbrains.rider.aspire.services.AspireHost

class ShowResourceGraphAction : AspireHostBaseAction() {
    override fun performAction(hostService: AspireHost, project: Project) {
        ResourceGraphService.getInstance(project).showResourceGraph(hostService)
    }

    override fun updateAction(event: AnActionEvent, hostService: AspireHost) {
        event.presentation.isEnabledAndVisible = hostService.isActive
    }
}