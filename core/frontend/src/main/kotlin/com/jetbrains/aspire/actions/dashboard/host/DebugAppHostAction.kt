package com.jetbrains.aspire.actions.dashboard.host

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.run.AspireRunManagerApi
import com.jetbrains.aspire.worker.AspireAppHostData
import com.jetbrains.aspire.worker.AspireAppHostStatus
import kotlinx.coroutines.launch

internal class DebugAppHostAction : AspireAppHostBaseAction() {
    override fun performAction(event: AnActionEvent, appHostData: AspireAppHostData, project: Project) {
        event.coroutineScope.launch {
            AspireRunManagerApi.getInstance().executeConfigurationForAppHost(project, appHostData.id, underDebug = true)
        }
    }

    override fun updateAction(event: AnActionEvent, appHostData: AspireAppHostData) {
        event.presentation.isVisible = true
        event.presentation.isEnabled = when (appHostData.status) {
            AspireAppHostStatus.Inactive,
            AspireAppHostStatus.Stopped -> true

            AspireAppHostStatus.Started -> false
        }
    }
}
