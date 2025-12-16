package com.jetbrains.aspire.actions.dashboard.host

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.dashboard.AspireAppHostViewModel
import com.jetbrains.aspire.worker.AspireAppHost

class AspireOpenDashboardAction : AspireHostBaseAction() {
    override fun performAction(appHost: AspireAppHost, project: Project) {
        val dashboardUrl = appHost.dashboardUrl.value
        if (dashboardUrl.isNullOrEmpty()) return

        BrowserUtil.browse(dashboardUrl)
    }

    override fun updateAction(event: AnActionEvent, appHostVm: AspireAppHostViewModel) {
        if (!appHostVm.isActive || appHostVm.dashboardUrl.isNullOrEmpty()) {
            event.presentation.isEnabledAndVisible = false
            return
        }

        event.presentation.isEnabledAndVisible = true
    }
}