package com.jetbrains.aspire.rider.orchestration

import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.workspaceModel.ide.toPath
import com.jetbrains.aspire.rider.util.isAspireSharedProject
import com.jetbrains.rider.model.RdProjectType
import com.jetbrains.rider.projectView.workspace.ProjectModelEntity
import com.jetbrains.rider.projectView.workspace.findProjects
import java.nio.file.Path

internal class MauiProjectOrchestrationHandler : BaseOrchestrationHandler() {
    companion object {
        private val LOG = logger<MauiProjectOrchestrationHandler>()

        private const val MAUI_CORE_PACKAGE_NAME = "Microsoft.Maui.Core"
    }

    override val priority = 1
    override val requiresProjectReference = false
    override val supportedProjectTypes = listOf(RdProjectType.MAUI)

    override val appHostPackageName = MAUI_HOSTING_PACKAGE_NAME
    override val appHostPackageVersion = MAUI_HOSTING_PACKAGE_VERSION

    override fun buildAppHostResourceCode(resourceName: String, relativeProjectPath: String) =
        "builder.AddMauiProject(\"$resourceName\", \"$relativeProjectPath\");"

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

    override suspend fun generateServiceDefaults(
        project: Project,
        projectEntities: List<ProjectModelEntity>
    ): Path? = generateAspireProject(project) {
        it.generateMauiServiceDefaults(projectEntities.mapNotNull { entity -> entity.url?.toPath() })
    }
}
