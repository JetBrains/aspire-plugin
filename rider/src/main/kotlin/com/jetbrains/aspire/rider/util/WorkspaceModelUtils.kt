package com.jetbrains.aspire.rider.util

import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.jetbrains.aspire.util.isAspireHostProject
import com.jetbrains.rider.projectView.workspace.ProjectModelEntity
import com.jetbrains.rider.projectView.workspace.findProjects

internal suspend fun findExistingAppHost(project: Project): ProjectModelEntity? {
    val dotnetProjects = project.serviceAsync<WorkspaceModel>().findProjects()
    return findExistingAppHost(dotnetProjects)
}

internal fun findExistingAppHost(dotnetProjects: List<ProjectModelEntity>): ProjectModelEntity? {
    return dotnetProjects.find { it.isAspireHostProject() }
}