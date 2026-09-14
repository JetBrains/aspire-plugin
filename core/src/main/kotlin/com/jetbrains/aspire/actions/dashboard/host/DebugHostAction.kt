package com.jetbrains.aspire.actions.dashboard.host

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.worker.AspireAppHostData
import com.jetbrains.aspire.worker.AspireAppHostLifecycleManager
import com.jetbrains.aspire.worker.AspireAppHostStatus
import kotlinx.coroutines.launch

class DebugHostAction : AspireHostDataAction() {
    override fun performAction(event: AnActionEvent, appHostData: AspireAppHostData, project: Project) {
        event.coroutineScope.launch {
            project.service<AspireAppHostLifecycleManager>().launchAppHost(appHostData.id, true)
        }
    }

    override fun updateAction(event: AnActionEvent, appHostData: AspireAppHostData) {
        event.presentation.isVisible = true
        event.presentation.isEnabled = when (appHostData.status) {
            AspireAppHostStatus.Inactive,
            AspireAppHostStatus.Stopped -> true

            AspireAppHostStatus.Starting,
            AspireAppHostStatus.Started -> false
        }
    }
}
