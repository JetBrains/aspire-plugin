package com.jetbrains.rider.aspire.actions.dashboard.host

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.jetbrains.rider.aspire.run.AspireRunConfigurationManager
import com.jetbrains.rider.aspire.dashboard.AspireHost

class RunHostAction : AspireHostBaseAction() {
    override fun performAction(hostService: AspireHost, project: Project) {
        AspireRunConfigurationManager.getInstance(project)
            .executeConfigurationForHost(hostService, false)
    }

    override fun updateAction(event: AnActionEvent, hostService: AspireHost) {
        event.presentation.isVisible = true
        event.presentation.isEnabled = !hostService.isActive
    }
}