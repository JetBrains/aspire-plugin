package com.jetbrains.rider.aspire.actions.dashboard.host

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.jetbrains.rider.aspire.services.AspireHost
import com.jetbrains.rider.aspire.worker.AspireWorkerManager
import com.jetbrains.rider.aspire.util.ASPIRE_HOST
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
        val aspireWorker = AspireWorkerManager.getInstance(project).aspireWorker
        return aspireWorker.getAspireHost(hostPath)
    }

    protected abstract fun updateAction(event: AnActionEvent, hostService: AspireHost)

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}