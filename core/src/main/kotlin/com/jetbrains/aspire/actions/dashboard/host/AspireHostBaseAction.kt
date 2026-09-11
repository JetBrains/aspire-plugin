package com.jetbrains.aspire.actions.dashboard.host

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.util.ASPIRE_APP_HOST
import com.jetbrains.aspire.worker.AspireAppHost

abstract class AspireHostBaseAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val appHost = event.getData(ASPIRE_APP_HOST) ?: return

        performAction(appHost, project)
    }

    protected abstract fun performAction(appHost: AspireAppHost, project: Project)

    override fun update(event: AnActionEvent) {
        val project = event.project
        val appHost = event.getData(ASPIRE_APP_HOST)
        if (project == null || appHost == null) {
            event.presentation.isEnabledAndVisible = false
            return
        }

        updateAction(event, appHost)
    }

    protected abstract fun updateAction(event: AnActionEvent, appHost: AspireAppHost)

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
