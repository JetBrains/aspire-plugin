package com.jetbrains.aspire.rider.actions.dashboard.resource

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.progress.currentThreadCoroutineScope
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.actions.dashboard.resource.AspireResourceBaseAction
import com.jetbrains.aspire.dashboard.AspireResource
import com.jetbrains.aspire.generated.ResourceState
import com.jetbrains.aspire.generated.ResourceType
import com.jetbrains.aspire.rider.debugger.AttachDebuggerService
import com.jetbrains.aspire.rider.sessions.SessionProfileModeService
import kotlinx.coroutines.launch

class AttachToProjectResourceAction : AspireResourceBaseAction() {
    override fun performAction(resourceService: AspireResource, dataContext: DataContext, project: Project) {
        val pid = resourceService.pid?.value ?: return
        currentThreadCoroutineScope().launch {
            AttachDebuggerService.getInstance(project).attach(pid)
        }
    }

    override fun updateAction(event: AnActionEvent, resourceService: AspireResource, project: Project) {
        val pid = resourceService.pid?.value
        val resourceId = resourceService.resourceId
        val isUnderDebugger = SessionProfileModeService
            .getInstance(project)
            .isSessionProfileUnderDebugger(resourceId)

        if (resourceService.type != ResourceType.Project ||
            resourceService.state != ResourceState.Running ||
            pid == null ||
            isUnderDebugger != false
        ) {
            event.presentation.isEnabledAndVisible = false
            return
        }

        event.presentation.isEnabledAndVisible = true
    }
}