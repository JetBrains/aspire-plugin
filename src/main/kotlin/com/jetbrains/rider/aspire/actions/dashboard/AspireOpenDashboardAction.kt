package com.jetbrains.rider.aspire.actions.dashboard

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.jetbrains.rider.aspire.services.AspireServiceManager
import com.jetbrains.rider.aspire.util.ASPIRE_HOST_PATH

class AspireOpenDashboardAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val hostPath = event.getData(ASPIRE_HOST_PATH) ?: return
        val hostService = AspireServiceManager
            .getInstance(project)
            .getHostService(hostPath)
            ?: return
        val dashboardUrl = hostService.dashboardUrl
        if (dashboardUrl.isNullOrEmpty()) return

        BrowserUtil.browse(dashboardUrl)
    }

    override fun update(event: AnActionEvent) {
        val project = event.project
        val hostPath = event.getData(ASPIRE_HOST_PATH)
        if (project == null || hostPath == null) {
            event.presentation.isEnabledAndVisible = false
            return
        }

        val hostService = AspireServiceManager
            .getInstance(project)
            .getHostService(hostPath)
        if (hostService == null || !hostService.isActive || hostService.dashboardUrl.isNullOrEmpty()) {
            event.presentation.isEnabledAndVisible = false
            return
        }

        event.presentation.isEnabledAndVisible = true
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}