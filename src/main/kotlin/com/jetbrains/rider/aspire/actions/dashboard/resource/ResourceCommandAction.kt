@file:Suppress("UnstableApiUsage")

package com.jetbrains.rider.aspire.actions.dashboard.resource

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.currentThreadCoroutineScope
import com.intellij.openapi.project.Project
import com.jetbrains.rider.aspire.generated.ResourceCommand
import com.jetbrains.rider.aspire.generated.ResourceCommandState
import com.jetbrains.rider.aspire.services.AspireResource
import kotlinx.coroutines.launch

abstract class ResourceCommandAction : AspireResourceBaseAction() {
    override fun performAction(resourceService: AspireResource, project: Project) {
        val command = findCommand(resourceService) ?: return

        if (command.state != ResourceCommandState.Enabled) return

        currentThreadCoroutineScope().launch {
            resourceService.executeCommand(command.commandType)
        }
    }

    override fun updateAction(event: AnActionEvent, resourceService: AspireResource, project: Project) {
        val command = findCommand(resourceService)
        if (command == null || command.state == ResourceCommandState.Hidden) {
            event.presentation.isEnabledAndVisible = false
            return
        }

        event.presentation.isVisible = true
        event.presentation.isEnabled = command.state == ResourceCommandState.Enabled
    }

    protected abstract fun findCommand(resource: AspireResource): ResourceCommand?
}