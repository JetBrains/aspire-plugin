@file:Suppress("UnstableApiUsage")

package com.jetbrains.rider.aspire.actions.dashboard

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.currentThreadCoroutineScope
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.workspaceModel.ide.toPath
import com.jetbrains.rider.aspire.AspireBundle
import com.jetbrains.rider.aspire.generated.ResourceState
import com.jetbrains.rider.aspire.generated.ResourceType
import com.jetbrains.rider.aspire.sessionHost.SessionManager
import com.jetbrains.rider.aspire.util.ASPIRE_RESOURCE_SERVICE_INSTANCE_ID
import com.jetbrains.rider.aspire.util.ASPIRE_RESOURCE_STATE
import com.jetbrains.rider.aspire.util.ASPIRE_RESOURCE_TYPE
import com.jetbrains.rider.projectView.workspace.containingProjectEntity
import com.jetbrains.rider.projectView.workspace.getProjectModelEntity
import kotlinx.coroutines.launch

class RestartResourceAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val serviceInstanceId = event.getData(ASPIRE_RESOURCE_SERVICE_INSTANCE_ID)

        if (serviceInstanceId != null) {
            val resourceType = event.getData(ASPIRE_RESOURCE_TYPE)
            val resourceState = event.getData(ASPIRE_RESOURCE_STATE)

            if (resourceType == ResourceType.Project && resourceState == ResourceState.Running) {
                currentThreadCoroutineScope().launch {
                    withBackgroundProgress(project, AspireBundle.message("progress.stop.resource")) {
                        SessionManager.getInstance(project).restartResource(serviceInstanceId, false)
                    }
                }
            }
        } else {
            val projectEntity = event.dataContext
                .getProjectModelEntity(true)
                ?.containingProjectEntity()
                ?: return
            val projectPath = projectEntity.url?.toPath() ?: return
            val resourceId = SessionManager.getInstance(project).getResourceIdByProject(projectPath) ?: return

            currentThreadCoroutineScope().launch {
                withBackgroundProgress(project, AspireBundle.message("progress.stop.resource")) {
                    SessionManager.getInstance(project).restartResource(resourceId, false)
                }
            }
        }
    }

    override fun update(event: AnActionEvent) {
        val project = event.project
        if (project == null) {
            event.presentation.isEnabledAndVisible = false
            return
        }

        val serviceInstanceId = event.getData(ASPIRE_RESOURCE_SERVICE_INSTANCE_ID)

        if (serviceInstanceId != null) {
            val resourceType = event.getData(ASPIRE_RESOURCE_TYPE)
            val resourceState = event.getData(ASPIRE_RESOURCE_STATE)

            if (resourceType != ResourceType.Project || resourceState != ResourceState.Running) {
                event.presentation.isEnabledAndVisible = false
                return
            }

            if (!SessionManager.getInstance(project).isResourceRunning(serviceInstanceId)) {
                event.presentation.isEnabledAndVisible = false
                return
            }

            event.presentation.isEnabledAndVisible = true
        } else {
            val projectEntity = event.dataContext.getProjectModelEntity(true)?.containingProjectEntity()

            if (projectEntity == null) {
                event.presentation.isEnabledAndVisible = false
                return
            }

            val projectPath = projectEntity.url?.toPath()
            if (projectPath == null) {
                event.presentation.isEnabledAndVisible = false
                return
            }

            val resourceId = SessionManager.getInstance(project).getResourceIdByProject(projectPath)
            if (resourceId == null) {
                event.presentation.isEnabledAndVisible = false
                return
            }

            if (!SessionManager.getInstance(project).isResourceRunning(resourceId)) {
                event.presentation.isEnabledAndVisible = false
                return
            }

            event.presentation.isEnabledAndVisible = true
        }
    }

    override fun getActionUpdateThread() = ActionUpdateThread.EDT
}