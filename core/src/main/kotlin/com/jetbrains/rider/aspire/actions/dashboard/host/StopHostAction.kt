package com.jetbrains.rider.aspire.actions.dashboard.host

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.jetbrains.rider.aspire.AspireService
import com.jetbrains.rider.aspire.run.AspireHostRunManager
import com.jetbrains.rider.aspire.dashboard.AspireHost
import kotlinx.coroutines.launch

class StopHostAction : AspireHostBaseAction() {
    override fun performAction(hostService: AspireHost, project: Project) {
        AspireService.getInstance(project).scope.launch {
            AspireHostRunManager.getInstance(project)
                .stopConfigurationForHost(hostService)
        }
    }

    override fun updateAction(event: AnActionEvent, hostService: AspireHost) {
        event.presentation.isVisible = true
        event.presentation.isEnabled = hostService.isActive
    }
}