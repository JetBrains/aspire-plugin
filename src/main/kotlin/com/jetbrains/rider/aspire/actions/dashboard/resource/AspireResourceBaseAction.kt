@file:Suppress("UnstableApiUsage")

package com.jetbrains.rider.aspire.actions.dashboard.resource

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.platform.workspace.jps.serialization.impl.toPath
import com.jetbrains.rider.aspire.services.AspireHostManager
import com.jetbrains.rider.aspire.services.AspireResource
import com.jetbrains.rider.aspire.util.ASPIRE_RESOURCE
import com.jetbrains.rider.projectView.workspace.containingProjectEntity
import com.jetbrains.rider.projectView.workspace.getProjectModelEntity

abstract class AspireResourceBaseAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val resource = event.getData(ASPIRE_RESOURCE) ?: getProjectResource(event) ?: return

        performAction(resource, project)
    }

    protected abstract fun performAction(resourceService: AspireResource, project: Project)

    override fun update(event: AnActionEvent) {
        val project = event.project
        val resource = event.getData(ASPIRE_RESOURCE) ?: getProjectResource(event)
        if (project == null || resource == null) {
            event.presentation.isEnabledAndVisible = false
            return
        }

        updateAction(event, resource)
    }

    protected abstract fun updateAction(event: AnActionEvent, resourceService: AspireResource)

    private fun getProjectResource(event: AnActionEvent): AspireResource? {
        val project = event.project ?: return null
        val projectEntity = event.dataContext.getProjectModelEntity(true)?.containingProjectEntity() ?: return null
        val projectPath = projectEntity.url?.toPath() ?: return null
        val resource = AspireHostManager.getInstance(project).getAspireResource(projectPath)

        return resource
    }

    override fun getActionUpdateThread() = ActionUpdateThread.EDT
}