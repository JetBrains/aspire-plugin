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
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.workspaceModel.ide.toPath
import com.jetbrains.aspire.rider.AspireRiderBundle
import com.jetbrains.aspire.rider.util.findExistingAppHost
import com.jetbrains.rd.platform.util.TimeoutTracker
import com.jetbrains.rider.ijent.extensions.toNioPath
import com.jetbrains.rider.projectView.workspace.ProjectModelEntity
import com.jetbrains.rider.projectView.workspace.findProjects
import com.jetbrains.rider.projectView.workspace.findProjectsByPath
import com.jetbrains.rider.services.RiderProjectModelWaiter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

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
        val existingAppHostEntity = findExistingAppHost(project)

        val appHostEntity = if (existingAppHostEntity != null) {
            LOG.trace { "Using existing AppHost" }
            existingAppHostEntity
        } else {
            LOG.trace { "Generating new AppHost project" }
            generateAppHost()
        }

        if (appHostEntity == null) {
            LOG.warn("Unable to find or generate AppHost project")
            return@withBackgroundProgress
        }

        val appHostFileWasModified = updateAppHostProject(appHostEntity, projectEntities)

        val projectsByType = projectEntities.groupBy { getProjectType(it) }
        var anyProjectModified = false

        for ((projectType, projects) in projectsByType) {
            if (projectType == null) continue

            val handler = AspireProjectOrchestrationHandler.getHandlerForType(projectType)
            if (handler != null) {
                LOG.trace { "Group of ${projects.size} projects of type $projectType using ${handler::class.simpleName}" }
                val modified = handler.generateServiceDefaultsAndModifyProjects(projects, project)
                anyProjectModified = anyProjectModified || modified
            } else {
                LOG.debug { "No handler found for project type: $projectType" }
            }
        }

        if (!appHostFileWasModified && !anyProjectModified) {
            notifyAboutAlreadyAddedOrchestration()
        }
    }

    private suspend fun generateAppHost(): ProjectModelEntity? {
        val appHostPath = generateAspireProject(project) { it.generateAppHost() } ?: return null
        withContext(Dispatchers.EDT) {
            try {
                val timeoutTracker = TimeoutTracker(60.seconds)
                RiderProjectModelWaiter.waitForWorkspaceModelReadySuspending(project, timeoutTracker)
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                LOG.warn("Failed to wait for WorkspaceModel to be ready", e)
                return@withContext
            }
        }

        val appHostProjectFile = LocalFileSystem.getInstance().findFileByNioFile(appHostPath) ?: return null
        return WorkspaceModel.getInstance(project).findProjectsByPath(appHostProjectFile).singleOrNull()
    }

    private suspend fun updateAppHostProject(
        appHostEntity: ProjectModelEntity,
        projectEntities: List<ProjectModelEntity>
    ): Boolean {
        val appHostProjectPath = appHostEntity.url?.toPath()
        if (appHostProjectPath == null) {
            LOG.warn("Unable to get AppHost project path")
            return false
        }

        val projectFilePathStrings = projectEntities.mapNotNull { it.url?.toPath() }
        val referenceByHostProjectResult =
            referenceByHostProject(project, appHostProjectPath, projectFilePathStrings) ?: return false

        val referencedProjectFilePaths =
            referenceByHostProjectResult.referencedProjectFilePaths.map { it.toNioPath() }

        val referencedProjectEntities = projectEntities.filter { entity ->
            entity.url?.toPath()?.let { path -> referencedProjectFilePaths.contains(path) } ?: false
        }

        return AspireAppHostModificationService
            .getInstance(project)
            .modifyAppHost(appHostEntity, referencedProjectEntities)
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