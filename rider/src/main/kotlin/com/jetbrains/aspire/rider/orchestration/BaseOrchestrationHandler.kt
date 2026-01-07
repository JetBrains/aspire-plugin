package com.jetbrains.aspire.rider.orchestration

import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.workspaceModel.ide.toPath
import com.jetbrains.aspire.util.isAspireSharedProject
import com.jetbrains.rider.ijent.extensions.toNioPath
import com.jetbrains.rider.projectView.workspace.ProjectModelEntity
import com.jetbrains.rider.projectView.workspace.findProjects
import java.nio.file.Path
import kotlin.io.path.absolutePathString

internal abstract class BaseOrchestrationHandler : AspireProjectOrchestrationHandler {
    companion object {
        private val LOG = logger<BaseOrchestrationHandler>()
    }

    override suspend fun generateServiceDefaultsAndModifyProjects(
        projectEntities: List<ProjectModelEntity>,
        project: Project
    ): Boolean {
        if (projectEntities.isEmpty()) return false

        LOG.debug { "Orchestrating ServiceDefaults for ${projectEntities.size} projects" }

        val existingServiceDefaultsPath = findExistingServiceDefaults(project)

        val serviceDefaultsPath = if (existingServiceDefaultsPath != null) {
            LOG.trace { "Using the existing ServiceDefaults project: ${existingServiceDefaultsPath.absolutePathString()}" }
            existingServiceDefaultsPath
        } else {
            LOG.trace { "Generating a new ServiceDefaults project" }
            generateServiceDefaults(project)
        }

        if (serviceDefaultsPath == null) {
            LOG.warn("Unable to find or generate ServiceDefaults project")
            return false
        }

        val projectFilePaths = projectEntities.mapNotNull { it.url?.toPath() }
        val referenceResult = referenceSharedProject(project, serviceDefaultsPath, projectFilePaths)
            ?: return false

        val projectsWithReference = referenceResult.projectFilePathsWithReference.map { path ->
            val projectPath = path.toNioPath()
            val entity = projectEntities.firstOrNull { entity -> entity.url?.toPath() == projectPath }
            projectPath to entity
        }

        val modified = AspireServiceDefaultsModificationService
            .getInstance(project)
            .insertAspireDefaultMethodsIntoProjects(projectsWithReference)

        LOG.debug { "ServiceDefaults orchestration completed. Modified: $modified" }
        return modified
    }

    protected open suspend fun findExistingServiceDefaults(project: Project): Path? {
        val dotnetProjects = project.serviceAsync<WorkspaceModel>().findProjects()
        for (dotnetProject in dotnetProjects) {
            if (dotnetProject.isAspireSharedProject()) {
                val projectFile = dotnetProject.url?.virtualFile?.toNioPath()
                if (projectFile == null) {
                    LOG.warn("Unable to find a virtual file for the Aspire ServiceDefaults")
                    continue
                }
                return projectFile
            }
        }
        return null
    }

    protected open suspend fun generateServiceDefaults(project: Project): Path? =
        generateAspireProject(project) { it.generateServiceDefaults() }
}
