package com.jetbrains.aspire.actions.dashboard.host

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.worker.AspireAppHost

class AspireOpenDashboardAction : AspireHostBaseAction() {
    override fun performAction(appHost: AspireAppHost, project: Project) {
        val dashboardUrl = getDashboardUrl(appHost)
        if (dashboardUrl.isNullOrEmpty()) return
        BrowserUtil.browse(dashboardUrl)
    }

    override fun updateAction(event: AnActionEvent, appHost: AspireAppHost) {
        val dashboardUrl = getDashboardUrl(appHost)
        event.presentation.isEnabledAndVisible = !dashboardUrl.isNullOrEmpty()
    }

    private fun getDashboardUrl(appHost: AspireAppHost): String? {
        return when (val appHostState = appHost.appHostState.value) {
            AspireAppHost.AspireAppHostState.Inactive -> null
            is AspireAppHost.AspireAppHostState.Starting -> appHostState.environment.aspireHostProjectUrl
            is AspireAppHost.AspireAppHostState.Started -> appHostState.environment.aspireHostProjectUrl
            AspireAppHost.AspireAppHostState.Stopped -> null
        }
    }
}
