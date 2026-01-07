package com.jetbrains.aspire.rider.orchestration

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.workspaceModel.ide.toPath
import com.jetbrains.aspire.util.isAspireSharedProject
import com.jetbrains.rider.model.RdNuGetProjects
import com.jetbrains.rider.model.RdProjectType
import com.jetbrains.rider.nuget.RiderNuGetFacade
import com.jetbrains.rider.projectView.workspace.ProjectModelEntity
import com.jetbrains.rider.projectView.workspace.findProjects
import com.jetbrains.rider.projectView.workspace.getId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.relativeTo

internal class MauiProjectOrchestrationHandler : BaseOrchestrationHandler() {
    companion object {
        private val LOG = logger<MauiProjectOrchestrationHandler>()

        private const val MAUI_CORE_PACKAGE_NAME = "Microsoft.Maui.Core"
    }

    override val priority = 1
    override val supportedProjectTypes = listOf(RdProjectType.MAUI)

    override suspend fun modifyAppHost(
        appHostEntity: ProjectModelEntity,
        projectEntities: List<ProjectModelEntity>,
        project: Project
    ): List<String> {
        installMauiHostingPackage(appHostEntity, project)

        val appHostProjectPath = appHostEntity.url?.toPath()
        if (appHostProjectPath == null) {
            LOG.warn("Unable to find AppHost project path. Skipping Maui nuget installation")
            return emptyList()
        }
        val projectPaths = projectEntities.mapNotNull { it.url?.toPath() }

        return buildList {
            for (projectPath in projectPaths.sorted()) {
                val projectName = projectPath.nameWithoutExtension
                val projectResourceName = projectName.replace('.', '-').lowercase()
                val relativeProjectPath = projectPath.relativeTo(appHostProjectPath.parent)

                val line = buildString {
                    append("builder.AddMauiProject(\"")
                    append(projectResourceName)
                    append("\", @\"")
                    append(relativeProjectPath)
                    append("\");")
                }
                add(line)
            }
        }
    }

    private suspend fun installMauiHostingPackage(appHostEntity: ProjectModelEntity, project: Project) {
        val appHostProjectId = appHostEntity.getId(project)
        if (appHostProjectId == null) {
            LOG.warn("Unable to find AppHost project id. Skipping Maui hosting nuget installation")
            return
        }

        if (isNuGetPackageInstalled(MAUI_HOSTING_PACKAGE_NAME, appHostProjectId, project) != false) {
            LOG.info("Maui hosting nuget package is already installed")
            return
        }

        val riderNuGetFacade = RiderNuGetFacade.getInstance(project)
        withContext(Dispatchers.EDT) {
            riderNuGetFacade.install(
                RdNuGetProjects(listOf(appHostProjectId)),
                MAUI_HOSTING_PACKAGE_NAME,
                MAUI_HOSTING_PACKAGE_VERSION
            )
        }
    }

    override suspend fun findExistingServiceDefaults(project: Project): Path? {
        val dotnetProjects = project.serviceAsync<WorkspaceModel>().findProjects()
        for (dotnetProject in dotnetProjects) {
            if (dotnetProject.isAspireSharedProject()) {
                val projectFile = dotnetProject.url?.virtualFile?.toNioPath()
                if (projectFile == null) {
                    LOG.warn("Unable to find a virtual file for the Aspire ServiceDefaults project")
                    continue
                }

                val isMauiCoreInstalled = isNuGetPackageInstalled(MAUI_CORE_PACKAGE_NAME, dotnetProject, project)
                if (isMauiCoreInstalled == true) {
                    return projectFile
                }
            }
        }
        return null
    }

    override suspend fun generateServiceDefaults(project: Project): Path? =
        generateAspireProject(project) { it.generateMauiServiceDefaults() }
}