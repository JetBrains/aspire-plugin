package com.jetbrains.aspire.rider.actions.dashboard.resource

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.actions.dashboard.resource.AspireResourceBaseAction
import com.jetbrains.aspire.worker.AspireResourceData
import com.jetbrains.aspire.worker.ResourceState
import com.jetbrains.aspire.worker.ResourceType
import com.jetbrains.aspire.rider.debugger.AttachDebuggerService
import com.jetbrains.aspire.rider.sessions.SessionProfileModeService
import com.jetbrains.aspire.worker.toNioPath
import kotlinx.coroutines.launch
import kotlin.io.path.absolutePathString

internal class AttachToProjectResourceAction : AspireResourceBaseAction() {
    override fun performAction(event: AnActionEvent, resourceData: AspireResourceData, project: Project) {
        val pid = resourceData.pid?.value ?: return
        event.coroutineScope.launch {
            AttachDebuggerService.getInstance(project).attach(pid)
        }
    }

    override fun updateAction(event: AnActionEvent, resourceData: AspireResourceData, project: Project) {
        val pid = resourceData.pid?.value
        val projectPath = resourceData.projectPath?.value
        if (resourceData.type != ResourceType.Project ||
            resourceData.state != ResourceState.Running ||
            pid == null ||
            projectPath == null
        ) {
            event.presentation.isEnabledAndVisible = false
            return
        }

        val isUnderDebugger = SessionProfileModeService
            .getInstance(project)
            .isSessionProfileUnderDebugger(projectPath.toNioPath().absolutePathString())

        if (isUnderDebugger != false) {
            event.presentation.isEnabledAndVisible = false
            return
        }

        event.presentation.isEnabledAndVisible = true
    }
}
