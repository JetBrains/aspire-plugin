package com.jetbrains.rider.aspire.diagram.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.jetbrains.rider.aspire.actions.dashboard.host.AspireHostBaseAction
import com.jetbrains.rider.aspire.dashboard.AspireHost
import com.jetbrains.rider.aspire.diagram.graph.ResourceGraphService

class ShowResourceGraphAction : AspireHostBaseAction() {
    override fun performAction(hostService: AspireHost, project: Project) {
        ResourceGraphService.getInstance(project).showResourceGraph(hostService)
    }

    override fun updateAction(event: AnActionEvent, hostService: AspireHost) {
        event.presentation.isEnabledAndVisible = hostService.isActive
    }
}