package me.rafaelldi.aspire.actions.dashboard

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.rd.util.lifetime
import com.jetbrains.rd.util.threading.coroutines.launch
import me.rafaelldi.aspire.diagram.DiagramService
import me.rafaelldi.aspire.sessionHost.AspireSessionHostManager
import me.rafaelldi.aspire.settings.AspireSettings
import me.rafaelldi.aspire.util.SESSION_HOST_ID

class ShowDiagramAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val sessionHostId = event.getData(SESSION_HOST_ID) ?: return
        val service = DiagramService.getInstance(project)
        project.lifetime.launch {
            service.showDiagram(sessionHostId)
        }
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

        val telemetryEnabled = AspireSettings.getInstance().collectTelemetry
        event.presentation.isEnabledAndVisible = telemetryEnabled
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}