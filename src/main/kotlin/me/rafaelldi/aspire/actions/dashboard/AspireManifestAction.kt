package me.rafaelldi.aspire.actions.dashboard

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import me.rafaelldi.aspire.manifest.ManifestService
import me.rafaelldi.aspire.sessionHost.AspireSessionHostManager
import me.rafaelldi.aspire.util.SESSION_HOST_ID

class AspireManifestAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val sessionHostId = event.getData(SESSION_HOST_ID) ?: return
        val sessionHost = AspireSessionHostManager
            .getInstance(project)
            .getAspireHost(sessionHostId)
            ?: return
        val hostPath = sessionHost.hostData.hostProjectPath ?: return

        ManifestService.getInstance(project).generateManifest(hostPath)
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
            .isAspireHostAvailable(sessionHostId)
        if (!hostAvailable) {
            event.presentation.isEnabledAndVisible = false
            return
        }

        event.presentation.isEnabledAndVisible = true
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}