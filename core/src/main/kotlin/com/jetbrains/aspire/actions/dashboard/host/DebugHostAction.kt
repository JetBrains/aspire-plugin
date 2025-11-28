package com.jetbrains.aspire.actions.dashboard.host

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.run.AspireRunConfigurationManager
import com.jetbrains.aspire.dashboard.AspireHost

class DebugHostAction : AspireHostBaseAction() {
    override fun performAction(hostService: AspireHost, project: Project) {
        AspireRunConfigurationManager.getInstance(project)
            .executeConfigurationForHost(hostService, true)
    }

    override fun updateAction(event: AnActionEvent, hostService: AspireHost) {
        event.presentation.isVisible = true
        event.presentation.isEnabled = !hostService.isActive
    }
}