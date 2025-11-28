@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.actions.dashboard.resource

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.progress.currentThreadCoroutineScope
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.generated.ResourceCommand
import com.jetbrains.aspire.generated.ResourceCommandState
import com.jetbrains.aspire.dashboard.AspireResource
import kotlinx.coroutines.launch

abstract class ResourceCommandAction : AspireResourceBaseAction() {
    override fun performAction(resourceService: AspireResource, dataContext: DataContext, project: Project) {
        val command = findCommand(resourceService) ?: return

        if (command.state != ResourceCommandState.Enabled) return

        currentThreadCoroutineScope().launch {
            beforeExecute(resourceService, project)
            resourceService.executeCommand(command.name)
        }
    }

    open fun beforeExecute(resourceService: AspireResource, project: Project) {
    }

    override fun updateAction(event: AnActionEvent, resourceService: AspireResource, project: Project) {
        val command = findCommand(resourceService)
        if (command == null || command.state == ResourceCommandState.Hidden) {
            event.presentation.isEnabledAndVisible = false
            return
        }

        val resourceState = checkResourceState(resourceService)
        if (!resourceState) {
            event.presentation.isEnabledAndVisible = false
            return
        }

        event.presentation.isVisible = true
        event.presentation.isEnabled = command.state == ResourceCommandState.Enabled
    }

    open fun checkResourceState(resourceService: AspireResource): Boolean {
        return true
    }

    protected abstract fun findCommand(resource: AspireResource): ResourceCommand?
}