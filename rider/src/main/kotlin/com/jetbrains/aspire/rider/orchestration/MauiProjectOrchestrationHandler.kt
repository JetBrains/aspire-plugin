package com.jetbrains.aspire.rider.orchestration

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.workspaceModel.ide.toPath
import com.jetbrains.aspire.rider.AspireRiderBundle
import com.jetbrains.aspire.util.isAspireSharedProject
import com.jetbrains.rider.ijent.extensions.toNioPath
import com.jetbrains.rider.model.RdProjectType
import com.jetbrains.rider.projectView.workspace.ProjectModelEntity
import com.jetbrains.rider.projectView.workspace.findProjects
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.absolutePathString

internal class MauiProjectOrchestrationHandler : AspireProjectOrchestrationHandler {
    companion object {
        private val LOG = logger<MauiProjectOrchestrationHandler>()
    }

    override val priority = 1
    override val supportedProjectTypes = listOf(RdProjectType.MAUI)

    override suspend fun generateServiceDefaultsAndModifyProjects(
        project: Project,
        projectEntities: List<ProjectModelEntity>
    ): Boolean {
        if (projectEntities.isEmpty()) return false

        LOG.debug { "Orchestrating Maui ServiceDefaults for ${projectEntities.size} MAUI projects" }

        val existingMauiServiceDefaultsPath = findExistingMauiServiceDefaults(project)

        val mauiServiceDefaultsPath = if (existingMauiServiceDefaultsPath != null) {
            LOG.trace { "Using existing Maui ServiceDefaults: ${existingMauiServiceDefaultsPath.absolutePathString()}" }
            existingMauiServiceDefaultsPath
        } else {
            LOG.trace { "Generating new Maui ServiceDefaults" }
            generateMauiServiceDefaults(project)
        }

        if (mauiServiceDefaultsPath == null) {
            LOG.warn("Unable to find or generate Maui ServiceDefaults")
            return false
        }

        val projectFilePathStrings = projectEntities.mapNotNull { it.url?.toPath() }
        val referenceResult = referenceSharedProject(project, mauiServiceDefaultsPath, projectFilePathStrings)
            ?: return false

        val projectsWithReference = referenceResult.projectFilePathsWithReference.map { path ->
            val projectPath = path.toNioPath()
            val entity = projectEntities.firstOrNull { entity -> entity.url?.toPath() == projectPath }
            projectPath to entity
        }

        val modified = AspireDefaultFileModificationService
            .getInstance(project)
            .insertAspireDefaultMethodsIntoProjects(projectsWithReference)

        LOG.debug { "Maui ServiceDefaults orchestration completed. Modified: $modified" }
        return modified
    }

    private suspend fun findExistingMauiServiceDefaults(project: Project): Path? {
        val dotnetProjects = project.serviceAsync<WorkspaceModel>().findProjects()
        for (dotnetProject in dotnetProjects) {
            if (dotnetProject.isAspireSharedProject()) {
                val projectFile = dotnetProject.url?.virtualFile?.toNioPath()
                if (projectFile == null) {
                    LOG.warn("Unable to find a virtual file for the Aspire SharedProject")
                    continue
                }

                val isMauiServiceDefaults = projectFile.fileName.toString().contains("Maui", ignoreCase = true)
                if (isMauiServiceDefaults) {
                    return projectFile
                }
            }
        }
        return null
    }

    private suspend fun generateMauiServiceDefaults(project: Project): Path? {
        val solutionId = findSolutionId(project)
        if (solutionId == null) {
            LOG.warn("Unable to find a solution for Maui ServiceDefaults generation")
            notifyAboutFailedGeneration(project)
            return null
        }

        val mauiServiceDefaultsPath = AspireProjectTemplateGenerator
            .getInstance(project)
            .generateMauiServiceDefaults()
        if (mauiServiceDefaultsPath == null) {
            LOG.warn("Unable to generate Maui ServiceDefaults project")
            notifyAboutFailedGeneration(project)
            return null
        }

        LOG.debug { "Generated Maui ServiceDefaults: ${mauiServiceDefaultsPath.absolutePathString()}" }

        addProjectToSolution(project, solutionId, mauiServiceDefaultsPath)

        return mauiServiceDefaultsPath
    }

    private suspend fun notifyAboutFailedGeneration(project: Project) = withContext(Dispatchers.EDT) {
        Notification(
            "Aspire",
            AspireRiderBundle.message("notification.unable.to.generate.aspire.projects"),
            "",
            NotificationType.ERROR
        )
            .notify(project)
    }
}