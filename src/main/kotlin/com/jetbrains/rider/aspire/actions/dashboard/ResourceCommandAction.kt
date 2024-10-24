@file:Suppress("UnstableApiUsage")

package com.jetbrains.rider.aspire.actions.dashboard

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.currentThreadCoroutineScope
import com.jetbrains.rider.aspire.generated.ResourceCommand
import com.jetbrains.rider.aspire.generated.ResourceCommandState
import com.jetbrains.rider.aspire.services.AspireResource
import com.jetbrains.rider.aspire.util.ASPIRE_RESOURCE
import kotlinx.coroutines.launch

abstract class ResourceCommandAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val resource = event.getData(ASPIRE_RESOURCE) ?: return
        val command = findCommand(resource) ?: return

        if (command.state != ResourceCommandState.Enabled) return

        currentThreadCoroutineScope().launch {
            resource.executeCommand(command.commandType)
        }
    }

    override fun update(event: AnActionEvent) {
        val resource = event.getData(ASPIRE_RESOURCE)
        if (resource == null) {
            event.presentation.isEnabledAndVisible = false
            return
        }

        val command = findCommand(resource)
        if (command == null || command.state == ResourceCommandState.Hidden) {
            event.presentation.isEnabledAndVisible = false
            return
        }

        event.presentation.isVisible = true
        event.presentation.isEnabled = command.state == ResourceCommandState.Enabled
    }

    protected abstract fun findCommand(resource: AspireResource): ResourceCommand?

    override fun getActionUpdateThread() = ActionUpdateThread.EDT
}