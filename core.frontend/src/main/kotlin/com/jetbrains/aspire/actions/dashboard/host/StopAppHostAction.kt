package com.jetbrains.aspire.actions.dashboard.host

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.run.AspireRunConfigurationManager
import com.jetbrains.aspire.worker.AspireAppHostData
import com.jetbrains.aspire.worker.AspireAppHostStatus

internal class StopAppHostAction : AspireAppHostBaseAction() {
    override fun performAction(event: AnActionEvent, appHostData: AspireAppHostData, project: Project) {
        project.service<AspireRunConfigurationManager>()
            .stopConfigurationForAppHost(appHostData.id)
    }

    override fun updateAction(event: AnActionEvent, appHostData: AspireAppHostData) {
        event.presentation.isVisible = true
        event.presentation.isEnabled = when (appHostData.status) {
            AspireAppHostStatus.Inactive,
            AspireAppHostStatus.Stopped -> false

            AspireAppHostStatus.Started -> true
        }
    }
}
