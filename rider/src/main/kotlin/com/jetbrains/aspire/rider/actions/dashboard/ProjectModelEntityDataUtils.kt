@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.rider.actions.dashboard

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataMap
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.jetbrains.rider.editors.getProjectModelId
import com.jetbrains.rider.projectView.ProjectModelDataKeys
import com.jetbrains.rider.projectView.workspace.ProjectModelEntity
import com.jetbrains.rider.projectView.workspace.getProjectModelEntities
import com.jetbrains.rider.projectView.workspace.getProjectModelEntity
import java.nio.file.Path

/**
 * Resolves the single Rider project model entity from a data context.
 *
 * The sources are checked from the cheapest to the most expensive one:
 * the selected project model entities, the project model id of the editor, and the file of the file editor.
 * The last one requires a workspace model lookup by file,
 * so it is performed only if [isCandidateFile] returns `true` for the file.
 */
internal fun getSingleProjectModelEntity(
    dataMap: DataMap,
    project: Project,
    isCandidateFile: (VirtualFile) -> Boolean = { true },
): ProjectModelEntity? {
    val projectModelEntityArray = dataMap[ProjectModelDataKeys.PROJECT_MODEL_ENTITY_ARRAY]
    if (!projectModelEntityArray.isNullOrEmpty()) {
        return projectModelEntityArray.singleOrNull()
    }

    val entityId = dataMap[CommonDataKeys.EDITOR]?.getProjectModelId()
    if (entityId != null) {
        return WorkspaceModel.getInstance(project).getProjectModelEntity(entityId)
    }

    val file = dataMap[PlatformCoreDataKeys.FILE_EDITOR]?.file ?: return null
    if (!isCandidateFile(file)) return null

    return WorkspaceModel
        .getInstance(project)
        .getProjectModelEntities(file, project)
        .singleOrNull()
}

/**
 * Checks whether the file is located in the directory of any of the [projectFilePaths].
 *
 * It is a cheap prefilter for [getSingleProjectModelEntity] that skips the workspace model lookup by file
 * for files that cannot belong to any of the projects.
 */
internal fun VirtualFile.isInsideDirectoryOfAny(projectFilePaths: Sequence<Path>): Boolean {
    val filePath = toNioPathOrNull() ?: return false
    return projectFilePaths.any { projectFilePath ->
        val projectDirectory = projectFilePath.parent ?: return@any false
        filePath.startsWith(projectDirectory)
    }
}
