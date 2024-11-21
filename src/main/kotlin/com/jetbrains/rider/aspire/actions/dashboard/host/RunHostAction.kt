package com.jetbrains.rider.aspire.actions.dashboard.host

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.jetbrains.rider.aspire.run.AspireHostRunManager
import com.jetbrains.rider.aspire.services.AspireHost

class RunHostAction : AspireHostBaseAction() {
    override fun performAction(hostService: AspireHost, project: Project) {
        AspireHostRunManager.getInstance(project)
            .executeConfigurationForHost(hostService, false)
    }

    override fun updateAction(event: AnActionEvent, hostService: AspireHost) {
        event.presentation.isVisible = true
        event.presentation.isEnabled = !hostService.isActive
    }
}