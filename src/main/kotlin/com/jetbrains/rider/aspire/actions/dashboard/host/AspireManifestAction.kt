package com.jetbrains.rider.aspire.actions.dashboard.host

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.jetbrains.rider.aspire.manifest.ManifestService
import com.jetbrains.rider.aspire.services.AspireHost

class AspireManifestAction : AspireHostBaseAction() {
    override fun performAction(hostService: AspireHost, project: Project) {
        val hostProjectPath = hostService.hostProjectPath
        ManifestService.getInstance(project).generateManifest(hostProjectPath)
    }

    override fun updateAction(event: AnActionEvent, hostService: AspireHost) {
        event.presentation.isEnabledAndVisible = true
    }
}