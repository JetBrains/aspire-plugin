package me.rafaelldi.aspire.actions

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import me.rafaelldi.aspire.AspireBundle

class OpenAspireDashboardAction(private val dashboardUrl: String?) : AnAction(
    AspireBundle.message("action.Aspire.Dashboard.Open.text"),
    AspireBundle.message("action.Aspire.Dashboard.Open.description"),
    AllIcons.General.Web
) {
    override fun actionPerformed(event: AnActionEvent) {
        if (dashboardUrl == null) return
        BrowserUtil.browse(dashboardUrl)
    }

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = dashboardUrl != null
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}