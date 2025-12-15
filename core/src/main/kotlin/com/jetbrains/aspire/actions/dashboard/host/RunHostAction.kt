package com.jetbrains.aspire.actions.dashboard.host

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.dashboard.AspireAppHostViewModel
import com.jetbrains.aspire.run.AspireRunConfigurationManager
import com.jetbrains.aspire.worker.AspireAppHost

class RunHostAction : AspireHostBaseAction() {
    override fun performAction(appHost: AspireAppHost, project: Project) {
        AspireRunConfigurationManager.getInstance(project)
            .executeConfigurationForHost(appHost, false)
    }

    override fun updateAction(event: AnActionEvent, appHostVm: AspireAppHostViewModel) {
        event.presentation.isVisible = true
        event.presentation.isEnabled = !appHostVm.isActive
    }
}