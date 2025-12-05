package com.jetbrains.aspire.actions.dashboard.host

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.dashboard.AspireHost
import com.jetbrains.aspire.util.ASPIRE_HOST
import com.jetbrains.aspire.worker.AspireWorker
import java.nio.file.Path

abstract class AspireHostBaseAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val hostPath = event.getData(ASPIRE_HOST)?.hostProjectPath ?: return
        val aspireHost = getAspireHost(hostPath, project) ?: return

        performAction(aspireHost, project)
    }

    protected abstract fun performAction(hostService: AspireHost, project: Project)

    override fun update(event: AnActionEvent) {
        val project = event.project
        val hostPath = event.getData(ASPIRE_HOST)?.hostProjectPath
        if (project == null || hostPath == null) {
            event.presentation.isEnabledAndVisible = false
            return
        }

        val aspireHost = getAspireHost(hostPath, project)
        if (aspireHost == null) {
            event.presentation.isEnabledAndVisible = false
            return
        }

        updateAction(event, aspireHost)
    }

    private fun getAspireHost(hostPath: Path, project: Project): AspireHost? {
        return AspireWorker.getInstance(project).getAppHostByPath(hostPath)
    }

    protected abstract fun updateAction(event: AnActionEvent, hostService: AspireHost)

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}