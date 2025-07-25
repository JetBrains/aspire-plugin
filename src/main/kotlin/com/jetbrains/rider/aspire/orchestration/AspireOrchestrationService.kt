package com.jetbrains.rider.aspire.orchestration

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.workspaceModel.ide.toPath
import com.jetbrains.rd.ide.model.RdPostProcessParameters
import com.jetbrains.rider.aspire.AspireBundle
import com.jetbrains.rider.aspire.generated.InsertDefaultMethodsIntoProjectProgramFileRequest
import com.jetbrains.rider.aspire.generated.InsertProjectsIntoAppHostFileRequest
import com.jetbrains.rider.aspire.generated.ReferenceProjectsFromAppHostRequest
import com.jetbrains.rider.aspire.generated.ReferenceServiceDefaultsFromProjectsRequest
import com.jetbrains.rider.aspire.generated.aspirePluginModel
import com.jetbrains.rider.aspire.util.isAspireHostProject
import com.jetbrains.rider.aspire.util.isAspireSharedProject
import com.jetbrains.rider.model.AddProjectCommand
import com.jetbrains.rider.model.projectModelTasks
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.projectView.workspace.*
import com.jetbrains.rider.run.configurations.runnableProjectsModelIfAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists

/**
 * Service to provide Aspire Orchestration support.
 *
 * This service is responsible for adding Aspire Orchestration features
 * to existing .NET projects by identifying existing Aspire-related projects,
 * generating new Aspire project files when necessary, and ensuring proper
 * integration of these files with selected .NET project entities.
 */
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
    suspend fun addAspireOrchestration(insertDefaultMethods: Boolean = false) {
        val projectEntities = project.serviceAsync<WorkspaceModel>()
            .findProjects()
            .filter { it.isProject() && !it.isAspireHostProject() && !it.isAspireSharedProject() }
        val runnableProjects = project.runnableProjectsModelIfAvailable?.projects?.valueOrNull
            ?: emptyList()
        val runnableProjectFilePaths = runnableProjects
            .groupBy { it.projectFilePath }
            .mapKeys { Path(it.key) }
        val runnableProjectEntities = projectEntities
            .filter { entity -> entity.url?.toPath()?.let { runnableProjectFilePaths.contains(it) } ?: false }

        withContext(Dispatchers.EDT) {
            val dialog = AddAspireOrchestrationDialog(project, runnableProjectEntities)
            if (dialog.showAndGet()) {
                val projectEntities = dialog.getSelectedItems()
                if (projectEntities.isEmpty()) return@withContext

                withContext(Dispatchers.Main) {
                    addAspireOrchestration(projectEntities, insertDefaultMethods)
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
        insertDefaultMethods: Boolean = false
    ) = withBackgroundProgress(project, AspireBundle.message("progress.adding.aspire.orchestration")) {
        var (hostProjectPath, sharedProjectPath) = findExistingAspireProjects()

        val needToGenerateAppHost = hostProjectPath == null
        val needToGenerateServiceDefaults = sharedProjectPath == null

        if (needToGenerateAppHost || needToGenerateServiceDefaults) {
            val (generatedHostProjectPath, generatedSharedProjectPath) =
                generateAspireProjects(needToGenerateAppHost, needToGenerateServiceDefaults)
                    ?: return@withBackgroundProgress

            LOG.debug { "Generated host: ${generatedHostProjectPath?.absolutePathString()}, generated shared project: ${generatedSharedProjectPath?.absolutePathString()}" }

            if (generatedHostProjectPath != null) hostProjectPath = generatedHostProjectPath
            if (generatedSharedProjectPath != null) sharedProjectPath = generatedSharedProjectPath
        }

        val projectFilePathStrings = projectEntities.mapNotNull { it.url?.toPath()?.absolutePathString() }
        if (hostProjectPath != null) {
            val referenceByHostProjectResult = referenceByHostProject(hostProjectPath, projectFilePathStrings)
            if (insertDefaultMethods) {
                referenceByHostProjectResult?.let {
                    insertProjectsIntoAppHostFile(hostProjectPath, it.referencedProjectFilePaths)
                }
            }
        }
        if (sharedProjectPath != null) {
            val referenceSharedProjectResult = referenceSharedProject(sharedProjectPath, projectFilePathStrings)
            if (insertDefaultMethods) {
                referenceSharedProjectResult?.let {
                    insertDefaultMethodsIntoProjects(it.projectFilePathsWithReference)
                }
            }
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
        needToGenerateAppHost: Boolean,
        needToGenerateServiceDefaults: Boolean,
    ): Pair<Path?, Path?>? {
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

        val (generatedHostProjectPath, generatedSharedProjectPath) = AspireProjectTemplateGenerator
            .getInstance(project)
            .generateAspireProjectsFromTemplates(
                needToGenerateAppHost,
                needToGenerateServiceDefaults
            )

        if (needToGenerateAppHost && generatedHostProjectPath == null || needToGenerateServiceDefaults && generatedSharedProjectPath == null) {
            LOG.warn("Some of the requested projects were not generated")
            notifyAboutFailedGeneration()
            return null
        }

        addGeneratedAspireProjectToSolution(solutionId, generatedHostProjectPath, generatedSharedProjectPath)

        return generatedHostProjectPath to generatedSharedProjectPath
    }

    private suspend fun addGeneratedAspireProjectToSolution(
        solutionId: Int,
        generatedHostProjectPath: Path?,
        generatedSharedProjectPath: Path?
    ) {
        val projects = buildList {
            generatedHostProjectPath?.let { add(it.absolutePathString()) }
            generatedSharedProjectPath?.let { add(it.absolutePathString()) }
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

    private suspend fun referenceByHostProject(hostProjectPath: Path, projectFilePaths: List<String>) =
        withContext(Dispatchers.EDT) {
            val request = ReferenceProjectsFromAppHostRequest(
                hostProjectPath.absolutePathString(),
                projectFilePaths
            )

            project.solution.aspirePluginModel.referenceProjectsFromAppHost.startSuspending(request)
        }

    private suspend fun referenceSharedProject(sharedProjectPath: Path, projectFilePaths: List<String>) =
        withContext(Dispatchers.EDT) {
            val request = ReferenceServiceDefaultsFromProjectsRequest(
                sharedProjectPath.absolutePathString(),
                projectFilePaths
            )

            project.solution.aspirePluginModel.referenceServiceDefaultsFromProjects.startSuspending(request)
        }

    private suspend fun insertProjectsIntoAppHostFile(hostProjectPath: Path, projects: List<String>) {
        if (projects.isEmpty()) return

        val appHostFilePath = hostProjectPath.parent.resolve("AppHost.cs")
        if (!appHostFilePath.exists()) {
            LOG.info("Unable to find AppHost.cs file")
            return
        }

        val appHostFile = VirtualFileManager.getInstance().findFileByNioPath(appHostFilePath)
        if (appHostFile == null) {
            LOG.warn("Unable to find AppHost.cs virtual file")
            return
        }

        withContext(Dispatchers.EDT) {
            FileEditorManager.getInstance(project).openFile(appHostFile, false)

            val request = InsertProjectsIntoAppHostFileRequest(
                hostProjectPath.absolutePathString(),
                projects,
            )
            project.solution.aspirePluginModel.insertProjectsIntoAppHostFile.startSuspending(request)
        }
    }

    private suspend fun insertDefaultMethodsIntoProjects(projectPaths: List<String>) {
        if (projectPaths.isEmpty()) return

        for (projectPath in projectPaths) {
            val projectFilePath = Path(projectPath)

            val projectProgramFilePath = projectFilePath.parent.resolve("Program.cs")
            if (!projectProgramFilePath.exists()) {
                LOG.info("Unable to Program.cs file for a project")
                continue
            }

            val projectProgramFile = VirtualFileManager.getInstance().findFileByNioPath(projectProgramFilePath)
            if (projectProgramFile == null) {
                LOG.warn("Unable to find Program.cs virtual file for a project")
                return
            }

            withContext(Dispatchers.EDT) {
                FileEditorManager.getInstance(project).openFile(projectProgramFile, false)

                val request = InsertDefaultMethodsIntoProjectProgramFileRequest(
                    projectFilePath.absolutePathString()
                )
                project.solution.aspirePluginModel.insertDefaultMethodsIntoProjectProgramFile.startSuspending(request)
            }
        }
    }
}