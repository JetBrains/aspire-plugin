package com.jetbrains.aspire.rider.orchestration

import com.intellij.diagnostic.rethrowControlFlowException
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
import com.jetbrains.aspire.rider.generated.InstallNuGetPackageRequest
import com.jetbrains.aspire.rider.generated.aspirePluginModel
import com.jetbrains.aspire.rider.util.isAspireSharedProject
import com.jetbrains.rider.ijent.extensions.toNioPath
import com.jetbrains.rider.ijent.extensions.toRd
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.projectView.workspace.ProjectModelEntity
import com.jetbrains.rider.projectView.workspace.findProjects
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.relativeToOrNull

internal abstract class BaseOrchestrationHandler : AspireProjectOrchestrationHandler {
    companion object {
        private val LOG = logger<BaseOrchestrationHandler>()
    }

    /**
     * The NuGet package that provides the AppHost API for the [supportedProjectTypes].
     */
    protected abstract val appHostPackageName: String
    protected abstract val appHostPackageVersion: String

    /**
     * Builds the code that registers a project as a resource in the AppHost main file.
     *
     * @param resourceName The name of the Aspire resource.
     * @param relativeProjectPath The project file path relative to the AppHost directory, with `/` separators.
     */
    protected abstract fun buildAppHostResourceCode(resourceName: String, relativeProjectPath: String): String

    final override suspend fun modifyAppHost(
        appHostEntity: ProjectModelEntity,
        projectEntities: List<ProjectModelEntity>,
        project: Project
    ): List<String> {
        val appHostProjectPath = appHostEntity.url?.toPath()
        if (appHostProjectPath == null) {
            LOG.warn("Unable to find AppHost project path. Skipping project registration")
            return emptyList()
        }

        installAppHostPackageIfMissing(project, appHostEntity, appHostProjectPath)

        val projectPaths = projectEntities.mapNotNull { it.url?.toPath() }

        return buildList {
            for (projectPath in projectPaths.sortedBy { it.nameWithoutExtension }) {
                val projectName = projectPath.nameWithoutExtension
                val projectResourceName = projectName.replace('.', '-').lowercase()
                val relativeProjectPath = projectPath.relativeToOrNull(appHostProjectPath.parent)
                if (relativeProjectPath == null) {
                    LOG.warn("Unable to find a path from the AppHost to the project: $projectPath")
                    continue
                }

                add(buildAppHostResourceCode(projectResourceName, relativeProjectPath.invariantSeparatorsPathString))
            }
        }
    }

    private suspend fun installAppHostPackageIfMissing(
        project: Project,
        appHostEntity: ProjectModelEntity,
        appHostProjectPath: Path,
    ) {
        if (isNuGetPackageInstalled(appHostPackageName, appHostEntity, project) != false) return

        val request = InstallNuGetPackageRequest(appHostProjectPath.toRd(), appHostPackageName, appHostPackageVersion)
        withContext(Dispatchers.EDT) {
            project.solution.aspirePluginModel.installNuGetPackage.startSuspending(request)
        }
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
            generateServiceDefaults(project, projectEntities)
        }

        if (serviceDefaultsPath == null) {
            LOG.warn("Unable to find or generate ServiceDefaults project")
            return false
        }

        val projectFilePaths = filterProjectsCompatibleWithServiceDefaults(
            project,
            serviceDefaultsPath,
            projectEntities.mapNotNull { it.url?.toPath() }
        )
        if (projectFilePaths.isEmpty()) {
            LOG.info("No projects compatible with the ServiceDefaults project")
            return false
        }

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

    /**
     * Returns the projects from [projectFilePaths] that can reference the ServiceDefaults project.
     *
     * ServiceDefaults is a library, so a project can reference it only if the project targets the same or a newer .NET.
     * Its framework may be higher than some selected projects require: an existing ServiceDefaults keeps its own framework,
     * and the template may not support the lowest selected framework.
     * Referencing it from such projects would break their build (NU1201).
     *
     * A project is compatible if each of its .NET target frameworks is greater than or equal to the lowest ServiceDefaults framework
     * (see [isCompatibleWithLibraryFrameworks]). Incompatible projects are skipped, and the user is notified about them.
     * If the target frameworks cannot be obtained, all projects are returned.
     */
    private suspend fun filterProjectsCompatibleWithServiceDefaults(
        project: Project,
        serviceDefaultsPath: Path,
        projectFilePaths: List<Path>
    ): List<Path> {
        val (serviceDefaultsFrameworks, projectFrameworks) = try {
            val serviceDefaultsResponse = getProjectsTargetFrameworks(project, listOf(serviceDefaultsPath))
            val projectsResponse = getProjectsTargetFrameworks(project, projectFilePaths)
            if (serviceDefaultsResponse == null || projectsResponse == null) {
                LOG.info("Unable to get target frameworks; skipping the ServiceDefaults compatibility check")
                return projectFilePaths
            }

            val serviceDefaultsFrameworks = serviceDefaultsResponse.projects.flatMap { it.targetFrameworks }
            val projectFrameworks = projectsResponse.projects.associate {
                it.projectFilePath.toNioPath() to it.targetFrameworks
            }
            serviceDefaultsFrameworks to projectFrameworks
        } catch (e: Exception) {
            rethrowControlFlowException(e)
            LOG.warn("Unable to get target frameworks; skipping the ServiceDefaults compatibility check", e)
            return projectFilePaths
        }

        val (compatibleProjects, incompatibleProjects) = projectFilePaths.partition { projectPath ->
            isCompatibleWithLibraryFrameworks(projectFrameworks[projectPath].orEmpty(), serviceDefaultsFrameworks)
        }

        if (incompatibleProjects.isNotEmpty()) {
            LOG.info("Projects incompatible with the ServiceDefaults ($serviceDefaultsFrameworks): $incompatibleProjects")
            notifyAboutIncompatibleProjects(project, serviceDefaultsPath, serviceDefaultsFrameworks, incompatibleProjects)
        }

        return compatibleProjects
    }

    private suspend fun notifyAboutIncompatibleProjects(
        project: Project,
        serviceDefaultsPath: Path,
        serviceDefaultsFrameworks: List<String>,
        incompatibleProjects: List<Path>
    ) = withContext(Dispatchers.EDT) {
        Notification(
            "Aspire",
            AspireRiderBundle.message("notification.incompatible.service.defaults.projects.title"),
            AspireRiderBundle.message(
                "notification.incompatible.service.defaults.projects.description",
                serviceDefaultsPath.nameWithoutExtension,
                serviceDefaultsFrameworks.sorted().joinToString(", "),
                incompatibleProjects.joinToString(", ") { it.nameWithoutExtension }
            ),
            NotificationType.WARNING
        )
            .notify(project)
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

    protected open suspend fun generateServiceDefaults(
        project: Project,
        projectEntities: List<ProjectModelEntity>
    ): Path? = generateAspireProject(project) {
        it.generateServiceDefaults(projectEntities.mapNotNull { entity -> entity.url?.toPath() })
    }
}
