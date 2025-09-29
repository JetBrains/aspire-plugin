package com.jetbrains.rider.aspire.actions.dashboard.host

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.currentThreadCoroutineScope
import com.intellij.openapi.project.Project
import com.jetbrains.rider.aspire.manifest.ManifestService
import com.jetbrains.rider.aspire.dashboard.AspireHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AspireManifestAction : AspireHostBaseAction() {
    override fun performAction(hostService: AspireHost, project: Project) {
        val hostProjectPath = hostService.hostProjectPath
        currentThreadCoroutineScope().launch(Dispatchers.Default) {
            ManifestService.getInstance(project).generateManifest(hostProjectPath)
        }
    }

    override fun updateAction(event: AnActionEvent, hostService: AspireHost) {
        event.presentation.isEnabledAndVisible = true
    }
}