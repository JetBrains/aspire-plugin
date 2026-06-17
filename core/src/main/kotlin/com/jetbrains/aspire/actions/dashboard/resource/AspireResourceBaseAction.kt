@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.actions.dashboard.resource

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.util.ASPIRE_RESOURCE
import com.jetbrains.aspire.worker.AspireResource
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class AspireResourceBaseAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val resource = event.getData(ASPIRE_RESOURCE) ?: return

        performAction(resource, event.dataContext, project)
    }

    protected abstract fun performAction(aspireResource: AspireResource, dataContext: DataContext, project: Project)

    override fun update(event: AnActionEvent) {
        val project = event.project
        val resource = event.getData(ASPIRE_RESOURCE)
        if (project == null || resource == null) {
            event.presentation.isEnabledAndVisible = false
            return
        }

        updateAction(event, resource, project)
    }

    protected abstract fun updateAction(event: AnActionEvent, aspireResource: AspireResource, project: Project)

    override fun getActionUpdateThread() = ActionUpdateThread.EDT
}
