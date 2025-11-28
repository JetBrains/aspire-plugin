package com.jetbrains.aspire.actions.dashboard.host

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.AspireService
import com.jetbrains.aspire.run.AspireRunConfigurationManager
import com.jetbrains.aspire.dashboard.AspireHost
import kotlinx.coroutines.launch

class StopHostAction : AspireHostBaseAction() {
    override fun performAction(hostService: AspireHost, project: Project) {
        AspireService.getInstance(project).scope.launch {
            AspireRunConfigurationManager.getInstance(project)
                .stopConfigurationForHost(hostService)
        }
    }

    override fun updateAction(event: AnActionEvent, hostService: AspireHost) {
        event.presentation.isVisible = true
        event.presentation.isEnabled = hostService.isActive
    }
}