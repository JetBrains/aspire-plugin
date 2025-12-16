package com.jetbrains.aspire.actions.dashboard.host

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.dashboard.AspireAppHostViewModel
import com.jetbrains.aspire.util.ASPIRE_APP_HOST
import com.jetbrains.aspire.worker.AspireAppHost
import com.jetbrains.aspire.worker.AspireWorker
import java.nio.file.Path

abstract class AspireHostBaseAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val appHostMainFilePath = event.getData(ASPIRE_APP_HOST)?.appHostMainFilePath ?: return
        val appHost = getAppHost(appHostMainFilePath, project) ?: return

        performAction(appHost, project)
    }

    protected abstract fun performAction(appHost: AspireAppHost, project: Project)

    override fun update(event: AnActionEvent) {
        val project = event.project
        val appHostVm = event.getData(ASPIRE_APP_HOST)
        if (project == null || appHostVm == null) {
            event.presentation.isEnabledAndVisible = false
            return
        }

        updateAction(event, appHostVm)

        val aspireHost = getAppHost(appHostVm.appHostMainFilePath, project)
        if (aspireHost == null) {
            event.presentation.isEnabledAndVisible = false
            return
        }
    }

    private fun getAppHost(hostPath: Path, project: Project): AspireAppHost? {
        return AspireWorker.getInstance(project).getAppHostByPath(hostPath)
    }

    protected abstract fun updateAction(event: AnActionEvent, appHostVm: AspireAppHostViewModel)

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}