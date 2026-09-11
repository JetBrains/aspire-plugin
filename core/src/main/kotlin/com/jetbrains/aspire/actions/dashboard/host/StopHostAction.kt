package com.jetbrains.aspire.actions.dashboard.host

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.worker.AspireAppHostData
import com.jetbrains.aspire.worker.AspireAppHostLifecycleManager
import com.jetbrains.aspire.worker.AspireAppHostStatus
import kotlinx.coroutines.launch

class StopHostAction : AspireHostDataAction() {
    override fun performAction(event: AnActionEvent, appHostData: AspireAppHostData, project: Project) {
        event.coroutineScope.launch {
            project.service<AspireAppHostLifecycleManager>().stopAppHost(appHostData.id)
        }
    }

    override fun updateAction(event: AnActionEvent, appHostData: AspireAppHostData) {
        event.presentation.isVisible = true
        event.presentation.isEnabled = when (appHostData.status) {
            AspireAppHostStatus.Inactive,
            AspireAppHostStatus.Stopped -> false

            AspireAppHostStatus.Starting,
            AspireAppHostStatus.Started -> true
        }
    }
}
