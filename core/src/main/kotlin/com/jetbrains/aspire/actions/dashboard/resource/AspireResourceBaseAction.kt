@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.actions.dashboard.resource

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.platform.workspace.jps.serialization.impl.toPath
import com.jetbrains.aspire.worker.AspireResource
import com.jetbrains.aspire.generated.ResourceType
import com.jetbrains.aspire.util.ASPIRE_RESOURCE
import com.jetbrains.aspire.util.findResource
import com.jetbrains.aspire.worker.AspireAppHost
import com.jetbrains.aspire.worker.AspireWorker
import com.jetbrains.rider.projectView.workspace.containingProjectEntity
import com.jetbrains.rider.projectView.workspace.getProjectModelEntity
import java.nio.file.Path

abstract class AspireResourceBaseAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val resource = event.getData(ASPIRE_RESOURCE)?.resource ?: getProjectResource(event) ?: return

        performAction(resource, event.dataContext, project)
    }

    protected abstract fun performAction(aspireResource: AspireResource, dataContext: DataContext, project: Project)

    override fun update(event: AnActionEvent) {
        val project = event.project
        val resource = event.getData(ASPIRE_RESOURCE)?.resource ?: getProjectResource(event)
        if (project == null || resource == null) {
            event.presentation.isEnabledAndVisible = false
            return
        }

        updateAction(event, resource, project)
    }

    protected abstract fun updateAction(event: AnActionEvent, aspireResource: AspireResource, project: Project)

    private fun getProjectResource(event: AnActionEvent): AspireResource? {
        val project = event.project ?: return null
        val projectEntity = event.dataContext.getProjectModelEntity(true)?.containingProjectEntity() ?: return null
        val projectPath = projectEntity.url?.toPath() ?: return null
        val aspireWorker = AspireWorker.getInstance(project)
        for (aspireHost in aspireWorker.appHosts.value) {
            val resource = getProjectResource(aspireHost, projectPath) ?: continue
            return resource
        }

        return null
    }

    private fun getProjectResource(aspireHost: AspireAppHost, projectPath: Path): AspireResource? {
        return aspireHost.findResource {
            val data = it.resourceState.value
            data.type == ResourceType.Project && data.projectPath?.value == projectPath
        }
    }

    override fun getActionUpdateThread() = ActionUpdateThread.EDT
}