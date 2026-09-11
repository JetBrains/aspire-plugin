@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.actions.dashboard.resource

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.actions.ASPIRE_RESOURCE_DATA
import com.jetbrains.aspire.worker.AspireResourceData
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class AspireResourceBaseAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val resourceData = event.getData(ASPIRE_RESOURCE_DATA) ?: return

        performAction(event, resourceData, project)
    }

    protected abstract fun performAction(event: AnActionEvent, resourceData: AspireResourceData, project: Project)

    override fun update(event: AnActionEvent) {
        val project = event.project
        val resourceData = event.getData(ASPIRE_RESOURCE_DATA)
        if (project == null || resourceData == null) {
            event.presentation.isEnabledAndVisible = false
            return
        }

        updateAction(event, resourceData, project)
    }

    protected abstract fun updateAction(event: AnActionEvent, resourceData: AspireResourceData, project: Project)

    override fun getActionUpdateThread() = ActionUpdateThread.EDT
}
