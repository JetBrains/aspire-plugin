package me.rafaelldi.aspire.actions.dashboard

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import me.rafaelldi.aspire.run.AspireHostRunManager
import me.rafaelldi.aspire.services.AspireServiceManager
import me.rafaelldi.aspire.util.ASPIRE_HOST_PATH

class RunHostAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val hostPath = event.getData(ASPIRE_HOST_PATH) ?: return
        val hostService = AspireServiceManager
            .getInstance(project)
            .getHostService(hostPath)
            ?: return

        AspireHostRunManager.getInstance(project)
            .executeConfigurationForHost(hostService, false)
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
        if (hostService == null) {
            event.presentation.isEnabledAndVisible = false
            return
        }

        event.presentation.isVisible = true
        event.presentation.isEnabled = !hostService.isActive
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}