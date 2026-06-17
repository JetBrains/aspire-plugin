@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.rider.actions.dashboard.resource

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataMap
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DataSnapshot
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.UiDataRule
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.serialization.impl.toPath
import com.jetbrains.aspire.util.ASPIRE_RESOURCE
import com.jetbrains.aspire.util.findProjectResource
import com.jetbrains.aspire.worker.AspireWorker
import com.jetbrains.rider.editors.getProjectModelId
import com.jetbrains.rider.projectView.ProjectModelDataKeys
import com.jetbrains.rider.projectView.workspace.ProjectModelEntity
import com.jetbrains.rider.projectView.workspace.getProjectModelEntities
import com.jetbrains.rider.projectView.workspace.getProjectModelEntity

/**
 * Enriches data contexts that expose a Rider project-model-entity (Solution Explorer, navigation bar,
 * Search Everywhere with an open editor, ...) with the matching [ASPIRE_RESOURCE].
 */
internal class AspireProjectResourceUiDataRule : UiDataRule {
    override fun uiDataSnapshot(sink: DataSink, snapshot: DataSnapshot) {
        val project = snapshot[CommonDataKeys.PROJECT] ?: return
        val projectEntity = getProjectModelEntities(snapshot, project) ?: return
        val projectPath = projectEntity.url?.toPath() ?: return
        val resource = AspireWorker.getInstance(project).findProjectResource(projectPath) ?: return

        sink[ASPIRE_RESOURCE] = resource
    }

    private fun getProjectModelEntities(dataMap: DataMap, project: Project): ProjectModelEntity? {
        val projectModelEntityArray = dataMap[ProjectModelDataKeys.PROJECT_MODEL_ENTITY_ARRAY]
        if (!projectModelEntityArray.isNullOrEmpty()) {
            return projectModelEntityArray.singleOrNull()
        }

        val entityId = dataMap[CommonDataKeys.EDITOR]?.getProjectModelId()
        if (entityId != null) {
            return WorkspaceModel.getInstance(project).getProjectModelEntity(entityId)
        }

        val fileEditor = dataMap[PlatformCoreDataKeys.FILE_EDITOR] ?: return null
        return WorkspaceModel
            .getInstance(project)
            .getProjectModelEntities(fileEditor.file, project)
            .singleOrNull()
    }
}
