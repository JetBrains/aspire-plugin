@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.actions.dashboard.resource

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.progress.currentThreadCoroutineScope
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.generated.ResourceCommand
import com.jetbrains.aspire.generated.ResourceCommandState
import com.jetbrains.aspire.worker.AspireResource
import com.jetbrains.aspire.worker.AspireResourceData
import kotlinx.coroutines.launch

abstract class ResourceCommandAction : AspireResourceBaseAction() {
    override fun performAction(aspireResource: AspireResource, dataContext: DataContext, project: Project) {
        val resourceData = aspireResource.resourceState.value
        val command = findCommand(resourceData) ?: return

        if (command.state != ResourceCommandState.Enabled) return

        currentThreadCoroutineScope().launch {
            beforeExecute(resourceData, project)
            aspireResource.executeCommand(command.name)
        }
    }

    open fun beforeExecute(resourceData: AspireResourceData, project: Project) {
    }

    override fun updateAction(event: AnActionEvent, aspireResource: AspireResource, project: Project) {
        val resourceData = aspireResource.resourceState.value
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