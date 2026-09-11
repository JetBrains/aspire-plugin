package com.jetbrains.aspire.actions.dashboard.host

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.actions.ASPIRE_APP_HOST_DATA
import com.jetbrains.aspire.worker.AspireAppHostData
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class AspireHostDataAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val appHostData = event.getData(ASPIRE_APP_HOST_DATA) ?: return

        performAction(event, appHostData, project)
    }

    protected abstract fun performAction(event: AnActionEvent, appHostData: AspireAppHostData, project: Project)

    override fun update(event: AnActionEvent) {
        val project = event.project
        val appHostData = event.getData(ASPIRE_APP_HOST_DATA)
        if (project == null || appHostData == null) {
            event.presentation.isEnabledAndVisible = false
            return
        }

        updateAction(event, appHostData)
    }

    protected abstract fun updateAction(event: AnActionEvent, appHostData: AspireAppHostData)

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
