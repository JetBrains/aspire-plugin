package com.jetbrains.aspire.diagram.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.jetbrains.aspire.actions.ASPIRE_APP_HOST_DATA
import com.jetbrains.aspire.diagram.graph.ResourceGraphService
import com.jetbrains.aspire.worker.AspireAppHostStatus
import kotlinx.coroutines.launch

class ShowResourceGraphAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val appHostData = event.getData(ASPIRE_APP_HOST_DATA) ?: return

        event.coroutineScope.launch {
            ResourceGraphService.getInstance(project).showResourceGraph(appHostData.id)
        }
    }

    override fun update(event: AnActionEvent) {
        val project = event.project
        val appHostData = event.getData(ASPIRE_APP_HOST_DATA)
        if (project == null || appHostData == null) {
            event.presentation.isEnabledAndVisible = false
            return
        }

        event.presentation.isEnabledAndVisible = when (appHostData.status) {
            AspireAppHostStatus.Inactive,
            AspireAppHostStatus.Stopped -> false

            AspireAppHostStatus.Starting,
            AspireAppHostStatus.Started -> true
        }
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
