package com.jetbrains.rider.aspire.actions.dashboard.host

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.jetbrains.rider.aspire.services.AspireHost
import com.jetbrains.rider.aspire.services.AspireHostManager
import com.jetbrains.rider.aspire.util.ASPIRE_HOST_PATH

abstract class AspireHostBaseAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val hostPath = event.getData(ASPIRE_HOST_PATH) ?: return
        val hostService = AspireHostManager
            .getInstance(project)
            .getAspireHost(hostPath)
            ?: return

        performAction(hostService, project)
    }

    protected abstract fun performAction(hostService: AspireHost, project: Project)

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

        updateAction(event, hostService)
    }

    protected abstract fun updateAction(event: AnActionEvent, hostService: AspireHost)

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}