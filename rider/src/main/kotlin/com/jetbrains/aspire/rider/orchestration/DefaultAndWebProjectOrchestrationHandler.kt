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
import com.jetbrains.aspire.rider.generated.ReferenceServiceDefaultsFromProjectsRequest
import com.jetbrains.aspire.rider.generated.aspirePluginModel
import com.jetbrains.aspire.util.isAspireSharedProject
import com.jetbrains.rd.ide.model.RdPostProcessParameters
import com.jetbrains.rider.ijent.extensions.toNioPath
import com.jetbrains.rider.ijent.extensions.toRd
import com.jetbrains.rider.model.AddProjectCommand
import com.jetbrains.rider.model.RdProjectType
import com.jetbrains.rider.model.projectModelTasks
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.projectView.workspace.ProjectModelEntity
import com.jetbrains.rider.projectView.workspace.findProjects
import com.jetbrains.rider.projectView.workspace.getId
import com.jetbrains.rider.projectView.workspace.getSolutionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.absolutePathString

internal class DefaultAndWebProjectOrchestrationHandler : AspireProjectOrchestrationHandler {
    companion object {
        private val LOG = logger<DefaultAndWebProjectOrchestrationHandler>()
    }

    override val priority = 0
    override val supportedProjectTypes = listOf(RdProjectType.Default, RdProjectType.Web)

    override suspend fun generateServiceDefaultsAndModifyProjects(
        project: Project,
        projectEntities: List<ProjectModelEntity>
    ): Boolean {
        if (projectEntities.isEmpty()) return false

        LOG.debug { "Orchestrating ServiceDefaults for ${projectEntities.size} Default/Web projects" }

        val existingServiceDefaultsPath = findExistingServiceDefaults(project)

        val serviceDefaultsPath = if (existingServiceDefaultsPath != null) {
            LOG.trace { "Using existing ServiceDefaults: ${existingServiceDefaultsPath.absolutePathString()}" }
            existingServiceDefaultsPath
        } else {
            LOG.trace { "Generating new ServiceDefaults for Default/Web projects" }
            generateAndAddServiceDefaults(project)
        }

        if (serviceDefaultsPath == null) {
            LOG.warn("Unable to find or generate ServiceDefaults for Default/Web projects")
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

        val modified = AspireDefaultFileModificationService
            .getInstance(project)
            .insertAspireDefaultMethodsIntoProjects(projectsWithReference)

        LOG.debug { "ServiceDefaults orchestration completed. Modified: $modified" }
        return modified
    }

    private suspend fun findExistingServiceDefaults(project: Project): Path? {
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

    private suspend fun generateAndAddServiceDefaults(project: Project): Path? {
        val solutionId = project.serviceAsync<WorkspaceModel>().getSolutionEntity()?.getId(project)
        if (solutionId == null) {
            LOG.warn("Unable to find a solution for ServiceDefaults generation")
            notifyAboutFailedGeneration(project)
            return null
        }

        val generatedProjectPaths = AspireProjectTemplateGenerator
            .getInstance(project)
            .generateAspireProjectsFromTemplates(
                generateAppHost = false,
                generateServiceDefaults = true,
                generateMauiServiceDefaults = false
            )

        val serviceDefaultsPath = generatedProjectPaths?.serviceDefaultsProjectPath
        if (serviceDefaultsPath == null) {
            LOG.warn("Unable to generate ServiceDefaults project")
            notifyAboutFailedGeneration(project)
            return null
        }

        LOG.debug { "Generated ServiceDefaults: ${serviceDefaultsPath.absolutePathString()}" }

        addProjectToSolution(project, solutionId, serviceDefaultsPath)

        return serviceDefaultsPath
    }

    private suspend fun addProjectToSolution(project: Project, solutionId: Int, projectPath: Path) {
        val parameters = RdPostProcessParameters(false, listOf())
        val command = AddProjectCommand(
            solutionId,
            listOf(projectPath.toRd()),
            emptyList(),
            true,
            parameters,
        )

        withContext(Dispatchers.EDT) {
            project.solution.projectModelTasks.addProject.startSuspending(command)
        }
    }

    private suspend fun referenceSharedProject(
        project: Project,
        sharedProjectPath: Path,
        projectFilePaths: List<Path>
    ) = withContext(Dispatchers.EDT) {
        val request = ReferenceServiceDefaultsFromProjectsRequest(
            sharedProjectPath.toRd(),
            projectFilePaths.map { it.toRd() }
        )

        project.solution.aspirePluginModel.referenceServiceDefaultsFromProjects.startSuspending(request)
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