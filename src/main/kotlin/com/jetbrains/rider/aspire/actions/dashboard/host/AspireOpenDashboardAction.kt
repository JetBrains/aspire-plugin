package com.jetbrains.rider.aspire.actions.dashboard.host

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.jetbrains.rider.aspire.services.AspireHost

class AspireOpenDashboardAction : AspireHostBaseAction() {
    override fun performAction(hostService: AspireHost, project: Project) {
        val dashboardUrl = hostService.dashboardUrl
        if (dashboardUrl.isNullOrEmpty()) return

        BrowserUtil.browse(dashboardUrl)
    }

    override fun updateAction(event: AnActionEvent, hostService: AspireHost) {
        if (!hostService.isActive || hostService.dashboardUrl.isNullOrEmpty()) {
            event.presentation.isEnabledAndVisible = false
            return
        }

        event.presentation.isEnabledAndVisible = true
    }
}