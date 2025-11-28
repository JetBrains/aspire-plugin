package com.jetbrains.aspire.orchestration

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.workspaceModel.ide.toPath
import com.jetbrains.rd.ide.model.RdPostProcessParameters
import com.jetbrains.aspire.AspireBundle
import com.jetbrains.aspire.generated.ReferenceProjectsFromAppHostRequest
import com.jetbrains.aspire.generated.ReferenceServiceDefaultsFromProjectsRequest
import com.jetbrains.aspire.generated.aspirePluginModel
import com.jetbrains.aspire.util.isAspireHostProject
import com.jetbrains.aspire.util.isAspireSharedProject
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
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

/**
 * Service to provide Aspire Orchestration support.
 *
 * This service is responsible for adding Aspire Orchestration features
 * to existing .NET projects by identifying existing Aspire-related projects,
 * generating new Aspire project files when necessary, and ensuring proper
 * integration of these files with selected .NET project entities.
 */
@Service(Service.Level.PROJECT)
internal class AspireOrchestrationService(private val project: Project) {
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
    ) = withBackgroundProgress(project, AspireBundle.message("progress.adding.aspire.orchestration")) {
        var (hostProjectPath, sharedProjectPath) = findExistingAspireProjects()
        var mauiSharedProjectPath: Path? = null

        val hasAppHost = hostProjectPath != null
        val hasServiceDefaults = sharedProjectPath != null

        if (!hasAppHost || !hasServiceDefaults) {
            val generatedAspireProjects = generateAspireProjects(projectEntities, hasAppHost, hasServiceDefaults)
                ?: return@withBackgroundProgress

            if (generatedAspireProjects.appHostProjectPath != null) hostProjectPath =
                generatedAspireProjects.appHostProjectPath
            if (generatedAspireProjects.serviceDefaultsProjectPath != null) sharedProjectPath =
                generatedAspireProjects.serviceDefaultsProjectPath
            if (generatedAspireProjects.mauiServiceDefaultsProjectPath != null) mauiSharedProjectPath =
                generatedAspireProjects.mauiServiceDefaultsProjectPath
        }

        val appHostFileWasModified = updateAppHostProject(hostProjectPath, projectEntities)
        val projectProgramFilesWereModified =
            updateProjectsWithSharedProjects(sharedProjectPath, mauiSharedProjectPath, projectEntities)

        if (hasAppHost && hasServiceDefaults && !appHostFileWasModified && !projectProgramFilesWereModified) {
            notifyAboutAlreadyAddedOrchestration()
        }
    }

    private suspend fun findExistingAspireProjects(): Pair<Path?, Path?> {
        var hostProjectPath: Path? = null
        var sharedProjectPath: Path? = null

        val dotnetProjects = project.serviceAsync<WorkspaceModel>().findProjects()

        for (dotnetProject in dotnetProjects) {
            if (dotnetProject.isAspireHostProject()) {
                val projectFile = dotnetProject.url?.virtualFile?.toNioPath()
                if (projectFile == null) {
                    LOG.warn("Unable to find a virtual file for the Aspire AppHost")
                    continue
                }
                hostProjectPath = projectFile
            } else if (dotnetProject.isAspireSharedProject()) {
                val projectFile = dotnetProject.url?.virtualFile?.toNioPath()
                if (projectFile == null) {
                    LOG.warn("Unable to find a virtual file for the Aspire SharedProject")
                    continue
                }
                sharedProjectPath = projectFile
            }
        }

        return hostProjectPath to sharedProjectPath
    }

    private suspend fun generateAspireProjects(
        projectEntities: List<ProjectModelEntity>,
        hasAppHost: Boolean,
        hasServiceDefaults: Boolean
    ): GeneratedAspireProjects? {
        val needToGenerateAppHost = !hasAppHost
        val needToGenerateServiceDefaults = !hasServiceDefaults && projectEntities.any {
            val descriptor = it.descriptor
            descriptor is RdProjectDescriptor && descriptor.specificType != RdProjectType.MAUI
        }
        val needToGenerateMauiServiceDefaults = !hasServiceDefaults && projectEntities.any {
            val descriptor = it.descriptor
            descriptor is RdProjectDescriptor && descriptor.specificType == RdProjectType.MAUI
        }

        val generatedAspireProjects = generateAspireProjects(
            needToGenerateAppHost,
            needToGenerateServiceDefaults,
            needToGenerateMauiServiceDefaults
        ) ?: return null

        LOG.debug {
            "Generated host: ${generatedAspireProjects.appHostProjectPath?.absolutePathString()}, " +
                    "generated shared project: ${generatedAspireProjects.serviceDefaultsProjectPath?.absolutePathString()}, " +
                    "generated maui shared project: ${generatedAspireProjects.mauiServiceDefaultsProjectPath?.absolutePathString()}"
        }

        return generatedAspireProjects
    }

    private suspend fun generateAspireProjects(
        needToGenerateAppHost: Boolean,
        needToGenerateServiceDefaults: Boolean,
        needToGenerateMauiServiceDefaults: Boolean
    ): GeneratedAspireProjects? {
        suspend fun notifyAboutFailedGeneration() = withContext(Dispatchers.EDT) {
            Notification(
                "Aspire",
                AspireBundle.message("notification.unable.to.generate.aspire.projects"),
                "",
                NotificationType.ERROR
            )
                .notify(project)
        }

        val solutionId = project.serviceAsync<WorkspaceModel>().getSolutionEntity()?.getId(project)
        if (solutionId == null) {
            LOG.warn("Unable to find a solution for the .NET Aspire project generation")
            notifyAboutFailedGeneration()
            return null
        }

        val generatedProjectPaths = AspireProjectTemplateGenerator
            .getInstance(project)
            .generateAspireProjectsFromTemplates(
                needToGenerateAppHost,
                needToGenerateServiceDefaults,
                needToGenerateMauiServiceDefaults
            )

        if (generatedProjectPaths == null) {
            LOG.warn("Unable to generate .NET Aspire projects")
            notifyAboutFailedGeneration()
            return null
        }

        val (hostProjectPath, sharedProjectPath, mauiSharedProjectPath) = generatedProjectPaths
        if (needToGenerateAppHost && hostProjectPath == null || needToGenerateServiceDefaults && sharedProjectPath == null) {
            LOG.warn("Some of the requested projects were not generated")
            notifyAboutFailedGeneration()
            return null
        }

        addGeneratedAspireProjectToSolution(solutionId, hostProjectPath, sharedProjectPath, mauiSharedProjectPath)

        return generatedProjectPaths
    }

    private suspend fun addGeneratedAspireProjectToSolution(
        solutionId: Int,
        hostProjectPath: Path?,
        sharedProjectPath: Path?,
        mauiSharedProjectPath: Path?
    ) {
        val projects = buildList {
            hostProjectPath?.let { add(it.absolutePathString()) }
            sharedProjectPath?.let { add(it.absolutePathString()) }
            mauiSharedProjectPath?.let { add(it.absolutePathString()) }
        }
        val parameters = RdPostProcessParameters(false, listOf())

        val command = AddProjectCommand(
            solutionId,
            projects,
            emptyList(),
            true,
            parameters,
        )

        withContext(Dispatchers.EDT) {
            project.solution.projectModelTasks.addProject.startSuspending(command)
        }
    }

    private suspend fun updateAppHostProject(
        hostProjectPath: Path?,
        projectEntities: List<ProjectModelEntity>
    ): Boolean {
        if (hostProjectPath == null) return false

        val projectFilePathStrings = projectEntities.mapNotNull { it.url?.toPath()?.absolutePathString() }
        val referenceByHostProjectResult =
            referenceByHostProject(hostProjectPath, projectFilePathStrings) ?: return false

        val referencedProjectFilePaths =
            referenceByHostProjectResult.referencedProjectFilePaths.map { path -> Path(path) }

        return AspireDefaultFileModificationService
            .getInstance(project)
            .insertProjectsIntoAppHostFile(hostProjectPath, referencedProjectFilePaths)

    }

    private suspend fun referenceByHostProject(hostProjectPath: Path, projectFilePaths: List<String>) =
        withContext(Dispatchers.EDT) {
            val request = ReferenceProjectsFromAppHostRequest(
                hostProjectPath.absolutePathString(),
                projectFilePaths
            )

            project.solution.aspirePluginModel.referenceProjectsFromAppHost.startSuspending(request)
        }

    private suspend fun updateProjectsWithSharedProjects(
        sharedProjectPath: Path?,
        mauiSharedProjectPath: Path?,
        projectEntities: List<ProjectModelEntity>
    ): Boolean {
        if (sharedProjectPath == null && mauiSharedProjectPath == null) return false

        var mauiProjectProgramFilesWereModified = false
        if (mauiSharedProjectPath != null) {
            val mauiProjects = projectEntities.filter {
                val descriptor = it.descriptor
                descriptor is RdProjectDescriptor && descriptor.specificType == RdProjectType.MAUI
            }

            mauiProjectProgramFilesWereModified = updateProjectsWithSharedProject(mauiSharedProjectPath, mauiProjects)
        }

        var projectProgramFilesWereModified = false
        if (sharedProjectPath != null) {
            val projects = projectEntities.filter {
                val descriptor = it.descriptor
                descriptor is RdProjectDescriptor && descriptor.specificType != RdProjectType.MAUI
            }

            projectProgramFilesWereModified = updateProjectsWithSharedProject(sharedProjectPath, projects)
        }

        return mauiProjectProgramFilesWereModified || projectProgramFilesWereModified
    }

    private suspend fun updateProjectsWithSharedProject(
        sharedProjectPath: Path,
        projectEntities: List<ProjectModelEntity>
    ): Boolean {
        val projectFilePathStrings = projectEntities.mapNotNull { it.url?.toPath()?.absolutePathString() }
        val referenceSharedProjectResult =
            referenceSharedProject(sharedProjectPath, projectFilePathStrings) ?: return false

        val projectsWithReference = referenceSharedProjectResult.projectFilePathsWithReference.map { path ->
            val projectPath = Path(path)
            val entity = projectEntities.firstOrNull { entity -> entity.url?.toPath() == projectPath }
            projectPath to entity
        }

        return AspireDefaultFileModificationService
            .getInstance(project)
            .insertAspireDefaultMethodsIntoProjects(projectsWithReference)
    }

    private suspend fun referenceSharedProject(sharedProjectPath: Path, projectFilePaths: List<String>) =
        withContext(Dispatchers.EDT) {
            val request = ReferenceServiceDefaultsFromProjectsRequest(
                sharedProjectPath.absolutePathString(),
                projectFilePaths
            )

            project.solution.aspirePluginModel.referenceServiceDefaultsFromProjects.startSuspending(request)
        }

    private suspend fun notifyAboutAlreadyAddedOrchestration() {
        withContext(Dispatchers.EDT) {
            Notification(
                "Aspire",
                AspireBundle.message("notification.selected.projects.contains.orchestration"),
                "",
                NotificationType.INFORMATION
            )
                .notify(project)
        }
    }
}