package com.jetbrains.aspire.actions.dashboard.host

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.worker.AspireAppHostData

class AspireOpenDashboardAction : AspireHostDataAction() {
    override fun performAction(event: AnActionEvent, appHostData: AspireAppHostData, project: Project) {
        val dashboardUrl = appHostData.dashboardUrl
        if (dashboardUrl.isNullOrEmpty()) return
        BrowserUtil.browse(dashboardUrl)
    }

    override fun updateAction(event: AnActionEvent, appHostData: AspireAppHostData) {
        event.presentation.isEnabledAndVisible = !appHostData.dashboardUrl.isNullOrEmpty()
    }
}
