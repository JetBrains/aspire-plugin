@file:Suppress("UnstableApiUsage")

package com.jetbrains.rider.aspire.actions.dashboard.resource

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.currentThreadCoroutineScope
import com.intellij.platform.workspace.jps.serialization.impl.toPath
import com.jetbrains.rider.aspire.generated.ResourceCommand
import com.jetbrains.rider.aspire.generated.ResourceCommandState
import com.jetbrains.rider.aspire.services.AspireHostManager
import com.jetbrains.rider.aspire.services.AspireResource
import com.jetbrains.rider.aspire.util.ASPIRE_RESOURCE
import com.jetbrains.rider.projectView.workspace.containingProjectEntity
import com.jetbrains.rider.projectView.workspace.getProjectModelEntity
import kotlinx.coroutines.launch

abstract class ResourceCommandAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val resource = event.getData(ASPIRE_RESOURCE) ?: getProjectResource(event) ?: return
        val command = findCommand(resource) ?: return

        if (command.state != ResourceCommandState.Enabled) return

        currentThreadCoroutineScope().launch {
            resource.executeCommand(command.commandType)
        }
    }

    override fun update(event: AnActionEvent) {
        val resource = event.getData(ASPIRE_RESOURCE) ?: getProjectResource(event)
        if (resource != null) {
            val command = findCommand(resource)
            if (command == null || command.state == ResourceCommandState.Hidden) {
                event.presentation.isEnabledAndVisible = false
                return
            }

            event.presentation.isVisible = true
            event.presentation.isEnabled = command.state == ResourceCommandState.Enabled
        } else {
            event.presentation.isEnabledAndVisible = false
            return
        }
    }

    private fun getProjectResource(event: AnActionEvent): AspireResource? {
        val project = event.project ?: return null
        val projectEntity = event.dataContext.getProjectModelEntity(true)?.containingProjectEntity() ?: return null
        val projectPath = projectEntity.url?.toPath() ?: return null
        val resource = AspireHostManager.getInstance(project).getAspireResource(projectPath)

        return resource
    }

    protected abstract fun findCommand(resource: AspireResource): ResourceCommand?

    override fun getActionUpdateThread() = ActionUpdateThread.EDT
}