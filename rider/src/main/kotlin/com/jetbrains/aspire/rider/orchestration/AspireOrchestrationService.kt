package com.jetbrains.aspire.rider.orchestration

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.workspaceModel.ide.toPath
import com.jetbrains.rd.ide.model.RdPostProcessParameters
import com.jetbrains.aspire.rider.generated.ReferenceProjectsFromAppHostRequest
import com.jetbrains.aspire.rider.generated.aspirePluginModel
import com.jetbrains.aspire.rider.AspireRiderBundle
import com.jetbrains.aspire.util.isAspireHostProject
import com.jetbrains.rider.ijent.extensions.toNioPath
import com.jetbrains.rider.ijent.extensions.toRd
import com.jetbrains.rider.model.AddProjectCommand
import com.jetbrains.rider.model.RdProjectDescriptor
import com.jetbrains.rider.model.RdProjectType
import com.jetbrains.rider.model.projectModelTasks
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.projectView.workspace.ProjectModelEntity
import com.jetbrains.rider.projectView.workspace.findProjects
import com.jetbrains.rider.projectView.workspace.getId
import com.jetbrains.rider.projectView.workspace.getSolutionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.absolutePathString

/**
 * Service to provide Aspire Orchestration support.
 *
 * This service is responsible for adding Aspire Orchestration features
 * to existing .NET projects by identifying existing Aspire-related projects,
 * generating new Aspire project files when necessary, and ensuring proper
 * integration of these files with selected .NET project entities.
 */
@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class AspireOrchestrationService(private val project: Project) {
    companion object {
        fun getInstance(project: Project): AspireOrchestrationService = project.service()
        private val LOG = logger<AspireOrchestrationService>()
    }

    /**
     * Adds .NET Aspire Orchestration.
     *
     * This method identifies potential .NET project entities.
     * A dialog is presented to a user for selecting specific projects to add .NET Aspire orchestration.
     */
    suspend fun addAspireOrchestration() {
        val projectEntities = project.serviceAsync<WorkspaceModel>()
            .findProjects()
            .filter { it.isAspireOrchestrationSupported() }

        withContext(Dispatchers.EDT) {
            val dialog = AddAspireOrchestrationDialog(project, projectEntities)
            if (dialog.showAndGet()) {
                val projectEntities = dialog.getSelectedItems()
                if (projectEntities.isEmpty()) return@withContext

                withContext(Dispatchers.Default) {
                    addAspireOrchestration(projectEntities)
                }
            }
        }
    }

    /**
     * Adds .NET Aspire Orchestration to the specified .NET projects.
     * This involves identifying existing Aspire projects,
     * generating necessary Aspire project files if they do not exist,
     * and referencing these projects from the provided list of .NET projects.
     *
     * @param projectEntities A list of .NET project entities to which Aspire Orchestration should be added.
     */
    suspend fun addAspireOrchestration(
        projectEntities: List<ProjectModelEntity>,
    ) = withBackgroundProgress(project, AspireRiderBundle.message("progress.adding.aspire.orchestration")) {
        val existingAppHostPath = findExistingAppHost()

        val appHostPath = if (existingAppHostPath != null) {
            LOG.trace { "Using existing AppHost: ${existingAppHostPath.absolutePathString()}" }
            existingAppHostPath
        } else {
            LOG.trace { "Generating new AppHost project" }
            generateAppHost()
        }

        if (appHostPath == null) {
            LOG.warn("Unable to find or generate AppHost project")
            return@withBackgroundProgress
        }

        val appHostFileWasModified = updateAppHostProject(appHostPath, projectEntities)

        val projectsByType = projectEntities.groupBy { getProjectType(it) }
        var anyProjectModified = false

        for ((projectType, projects) in projectsByType) {
            if (projectType == null) continue

            val handler = AspireProjectOrchestrationHandler.getHandlerForType(projectType)
            if (handler != null) {
                LOG.trace { "Group of ${projects.size} projects of type $projectType using ${handler::class.simpleName}" }
                val modified = handler.generateServiceDefaultsAndModifyProjects(project, projects)
                anyProjectModified = anyProjectModified || modified
            } else {
                LOG.debug { "No handler found for project type: $projectType" }
            }
        }

        if (!appHostFileWasModified && !anyProjectModified) {
            notifyAboutAlreadyAddedOrchestration()
        }
    }

    private fun getProjectType(entity: ProjectModelEntity): RdProjectType? {
        val descriptor = entity.descriptor
        return if (descriptor is RdProjectDescriptor) {
            descriptor.specificType
        } else {
            null
        }
    }

    private suspend fun findExistingAppHost(): Path? {
        val dotnetProjects = project.serviceAsync<WorkspaceModel>().findProjects()
        for (dotnetProject in dotnetProjects) {
            if (dotnetProject.isAspireHostProject()) {
                val projectFile = dotnetProject.url?.virtualFile?.toNioPath()
                if (projectFile == null) {
                    LOG.warn("Unable to find a virtual file for the Aspire AppHost")
                    continue
                }
                return projectFile
            }
        }
        return null
    }

    private suspend fun generateAppHost(): Path? {
        val solutionId = project.serviceAsync<WorkspaceModel>().getSolutionEntity()?.getId(project)
        if (solutionId == null) {
            LOG.warn("Unable to find a solution for the .NET Aspire project generation")
            notifyAboutFailedGeneration()
            return null
        }

        val hostProjectPath = AspireProjectTemplateGenerator
            .getInstance(project)
            .generateAppHost()
        if (hostProjectPath == null) {
            LOG.warn("Unable to generate AppHost project")
            notifyAboutFailedGeneration()
            return null
        }

        LOG.debug { "Generated AppHost: ${hostProjectPath.absolutePathString()}" }

        addProjectToSolution(solutionId, hostProjectPath)

        return hostProjectPath
    }

    private suspend fun addProjectToSolution(solutionId: Int, projectPath: Path) {
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

    private suspend fun updateAppHostProject(
        hostProjectPath: Path,
        projectEntities: List<ProjectModelEntity>
    ): Boolean {
        val projectFilePathStrings = projectEntities.mapNotNull { it.url?.toPath() }
        val referenceByHostProjectResult =
            referenceByHostProject(hostProjectPath, projectFilePathStrings) ?: return false

        val referencedProjectFilePaths =
            referenceByHostProjectResult.referencedProjectFilePaths.map { it.toNioPath() }

        return AspireDefaultFileModificationService
            .getInstance(project)
            .insertProjectsIntoAppHostFile(hostProjectPath, referencedProjectFilePaths)
    }

    private suspend fun referenceByHostProject(hostProjectPath: Path, projectFilePaths: List<Path>) =
        withContext(Dispatchers.EDT) {
            val request = ReferenceProjectsFromAppHostRequest(
                hostProjectPath.toRd(),
                projectFilePaths.map { it.toRd() }
            )

            project.solution.aspirePluginModel.referenceProjectsFromAppHost.startSuspending(request)
        }

    private suspend fun notifyAboutFailedGeneration() = withContext(Dispatchers.EDT) {
        Notification(
            "Aspire",
            AspireRiderBundle.message("notification.unable.to.generate.aspire.projects"),
            "",
            NotificationType.WARNING
        )
            .notify(project)
    }

    private suspend fun notifyAboutAlreadyAddedOrchestration() = withContext(Dispatchers.EDT) {
        Notification(
            "Aspire",
            AspireRiderBundle.message("notification.selected.projects.contains.orchestration"),
            "",
            NotificationType.INFORMATION
        )
            .notify(project)
    }
}