@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.actions.dashboard.resource

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.worker.AspireResourceCommandExecutor
import com.jetbrains.aspire.worker.ResourceCommand
import com.jetbrains.aspire.worker.ResourceCommandState
import com.jetbrains.aspire.worker.AspireResourceData
import kotlinx.coroutines.launch

internal abstract class ResourceCommandAction : AspireResourceBaseAction() {
    override fun performAction(event: AnActionEvent, resourceData: AspireResourceData, project: Project) {
        val command = findCommand(resourceData) ?: return

        if (command.state != ResourceCommandState.Enabled) return

        event.coroutineScope.launch {
            beforeExecute(resourceData, project)
            project.service<AspireResourceCommandExecutor>().executeCommand(resourceData.id, command.name)
        }
    }

    open fun beforeExecute(resourceData: AspireResourceData, project: Project) {
    }

    override fun updateAction(event: AnActionEvent, resourceData: AspireResourceData, project: Project) {
        val command = findCommand(resourceData)
        if (command == null || command.state == ResourceCommandState.Hidden) {
            event.presentation.isEnabledAndVisible = false
            return
        }

        val resourceState = checkResourceState(resourceData)
        if (!resourceState) {
            event.presentation.isEnabledAndVisible = false
            return
        }

        event.presentation.isVisible = true
        event.presentation.isEnabled = command.state == ResourceCommandState.Enabled
    }

    open fun checkResourceState(resourceData: AspireResourceData): Boolean {
        return true
    }

    protected abstract fun findCommand(resourceData: AspireResourceData): ResourceCommand?
}
