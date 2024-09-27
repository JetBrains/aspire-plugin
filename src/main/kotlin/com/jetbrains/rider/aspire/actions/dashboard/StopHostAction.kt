package com.jetbrains.rider.aspire.actions.dashboard

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.jetbrains.rider.aspire.AspireService
import com.jetbrains.rider.aspire.run.AspireHostRunManager
import com.jetbrains.rider.aspire.services.AspireHostManager
import com.jetbrains.rider.aspire.util.ASPIRE_HOST_PATH
import kotlinx.coroutines.launch

class StopHostAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val hostPath = event.getData(ASPIRE_HOST_PATH) ?: return
        val hostService = AspireHostManager
            .getInstance(project)
            .getAspireHost(hostPath)
            ?: return

        AspireService.getInstance(project).scope.launch {
            AspireHostRunManager.getInstance(project)
                .stopConfigurationForHost(hostService)
        }
    }

    override fun update(event: AnActionEvent) {
        val project = event.project
        val hostPath = event.getData(ASPIRE_HOST_PATH)
        if (project == null || hostPath == null) {
            event.presentation.isEnabledAndVisible = false
            return
        }

        val hostService = AspireHostManager
            .getInstance(project)
            .getAspireHost(hostPath)
        if (hostService == null) {
            event.presentation.isEnabledAndVisible = false
            return
        }

        event.presentation.isVisible = true
        event.presentation.isEnabled = hostService.isActive
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}