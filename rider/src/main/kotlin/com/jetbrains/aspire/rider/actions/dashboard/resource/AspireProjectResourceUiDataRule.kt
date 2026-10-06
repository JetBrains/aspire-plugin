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
import com.jetbrains.aspire.actions.ASPIRE_RESOURCE_DATA
import com.jetbrains.aspire.util.findProjectResource
import com.jetbrains.aspire.worker.AspireWorker
import com.jetbrains.rider.editors.getProjectModelId
import com.jetbrains.rider.projectView.ProjectModelDataKeys
import com.jetbrains.rider.projectView.workspace.ProjectModelEntity
import com.jetbrains.rider.projectView.workspace.getProjectModelEntities
import com.jetbrains.rider.projectView.workspace.getProjectModelEntity

/**
 * Adds the matching [ASPIRE_RESOURCE_DATA] value to data contexts that contain a Rider project entity.
 */
internal class AspireProjectResourceUiDataRule : UiDataRule {
    @Suppress("JetBrainsInternalApiUsage")
    override fun uiDataSnapshot(sink: DataSink, snapshot: DataSnapshot) {
        val project = snapshot[CommonDataKeys.PROJECT] ?: return
        sink.lazyValue(ASPIRE_RESOURCE_DATA) { dataMap ->
            val projectEntity = getProjectModelEntities(dataMap, project) ?: return@lazyValue null
            val projectPath = projectEntity.url?.toPath() ?: return@lazyValue null
            val resource = AspireWorker.getInstance(project).findProjectResource(projectPath) ?: return@lazyValue null
            resource.data.value
        }
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

        val file = dataMap[PlatformCoreDataKeys.FILE_EDITOR]?.file ?: return null
        return WorkspaceModel
            .getInstance(project)
            .getProjectModelEntities(file, project)
            .singleOrNull()
    }
}
