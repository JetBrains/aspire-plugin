package com.jetbrains.rider.aspire.actions.dashboard

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.jetbrains.rider.aspire.manifest.ManifestService
import com.jetbrains.rider.aspire.services.a.AspireHostManager
import com.jetbrains.rider.aspire.util.ASPIRE_HOST_PATH

class AspireManifestAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val hostPath = event.getData(ASPIRE_HOST_PATH) ?: return
        val hostService = AspireHostManager
            .getInstance(project)
            .getAspireHost(hostPath)
            ?: return
        val hostProjectPath = hostService.hostProjectPath

        ManifestService.getInstance(project).generateManifest(hostProjectPath)
    }

    override fun update(event: AnActionEvent) {
        val project = event.project
        val hostPath = event.getData(ASPIRE_HOST_PATH)
        if (project == null || hostPath == null) {
            event.presentation.isEnabledAndVisible = false
            return
        }

        val hostService = AspireHostManager
            .getInstance(project)
            .getAspireHost(hostPath)
        if (hostService == null) {
            event.presentation.isEnabledAndVisible = false
            return
        }

        event.presentation.isEnabledAndVisible = true
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}