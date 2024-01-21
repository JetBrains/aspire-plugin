package me.rafaelldi.aspire.actions

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import me.rafaelldi.aspire.sessionHost.AspireSessionHostManager
import me.rafaelldi.aspire.util.SESSION_HOST_ID

class OpenAspireDashboardAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val sessionHostId = event.getData(SESSION_HOST_ID) ?: return
        val sessionHost = AspireSessionHostManager
            .getInstance(project)
            .getSessionHost(sessionHostId)
            ?: return
        val dashboardUrl = sessionHost.hostData.dashboardUrl
        if (dashboardUrl.isNullOrEmpty()) return

        BrowserUtil.browse(dashboardUrl)
    }

    override fun update(event: AnActionEvent) {
        val project = event.project
        val sessionHostId = event.getData(SESSION_HOST_ID)
        if (project == null || sessionHostId == null) {
            event.presentation.isEnabledAndVisible = false
            return
        }

        val hostAvailable = AspireSessionHostManager
            .getInstance(project)
            .isSessionHostAvailable(sessionHostId)
        if (!hostAvailable) {
            event.presentation.isEnabledAndVisible = false
            return
        }

        event.presentation.isEnabledAndVisible = true
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}