package com.jetbrains.aspire.rider.actions.dashboard.resource

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.progress.currentThreadCoroutineScope
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.actions.dashboard.resource.AspireResourceBaseAction
import com.jetbrains.aspire.worker.AspireResource
import com.jetbrains.aspire.generated.ResourceState
import com.jetbrains.aspire.generated.ResourceType
import com.jetbrains.aspire.rider.debugger.AttachDebuggerService
import com.jetbrains.aspire.rider.sessions.SessionProfileModeService
import kotlinx.coroutines.launch
import kotlin.io.path.absolutePathString

class AttachToProjectResourceAction : AspireResourceBaseAction() {
    override fun performAction(aspireResource: AspireResource, dataContext: DataContext, project: Project) {
        val resourceData = aspireResource.resourceState.value
        val pid = resourceData.pid?.value ?: return
        currentThreadCoroutineScope().launch {
            AttachDebuggerService.getInstance(project).attach(pid)
        }
    }

    override fun updateAction(event: AnActionEvent, aspireResource: AspireResource, project: Project) {
        val resourceData = aspireResource.resourceState.value
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
            .isSessionProfileUnderDebugger(projectPath.absolutePathString())

        if (isUnderDebugger != false) {
            event.presentation.isEnabledAndVisible = false
            return
        }

        event.presentation.isEnabledAndVisible = true
    }
}