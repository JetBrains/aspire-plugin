@file:Suppress("UnstableApiUsage")

package com.jetbrains.rider.aspire.actions.dashboard.resource

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.currentThreadCoroutineScope
import com.intellij.openapi.project.Project
import com.jetbrains.rider.aspire.debugger.AttachDebuggerService
import com.jetbrains.rider.aspire.generated.ResourceState
import com.jetbrains.rider.aspire.generated.ResourceType
import com.jetbrains.rider.aspire.services.AspireResource
import kotlinx.coroutines.launch

class AttachToProjectResourceAction : AspireResourceBaseAction() {
    override fun performAction(resourceService: AspireResource, project: Project) {
        val pid = resourceService.pid ?: return
        currentThreadCoroutineScope().launch {
            AttachDebuggerService.getInstance(project).attach(pid)
        }
    }

    override fun updateAction(event: AnActionEvent, resourceService: AspireResource, project: Project) {
        val pid = resourceService.pid
        val isUnderDebugger = resourceService.isUnderDebugger

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