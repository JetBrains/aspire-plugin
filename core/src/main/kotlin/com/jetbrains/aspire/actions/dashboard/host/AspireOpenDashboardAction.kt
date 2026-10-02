package com.jetbrains.aspire.actions.dashboard.host

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.jetbrains.aspire.actions.ASPIRE_APP_HOST_DASHBOARD_URL

class AspireOpenDashboardAction : DumbAwareAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val dashboardUrl = event.getData(ASPIRE_APP_HOST_DASHBOARD_URL)
        if (dashboardUrl.isNullOrEmpty()) return
        BrowserUtil.browse(dashboardUrl)
    }

    override fun update(event: AnActionEvent) {
        val dashboardUrl = event.getData(ASPIRE_APP_HOST_DASHBOARD_URL)
        event.presentation.isEnabledAndVisible = !dashboardUrl.isNullOrEmpty()
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
