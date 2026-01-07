package com.jetbrains.aspire.rider.orchestration

import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.workspaceModel.ide.toPath
import com.jetbrains.rider.model.RdNuGetProjects
import com.jetbrains.rider.model.RdProjectType
import com.jetbrains.rider.nuget.RiderNuGetFacade
import com.jetbrains.rider.projectView.workspace.ProjectModelEntity
import com.jetbrains.rider.projectView.workspace.getId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.io.path.nameWithoutExtension

internal class AzureFunctionOrchestrationHandler : BaseOrchestrationHandler() {
    companion object {
        private val LOG = logger<AzureFunctionOrchestrationHandler>()

        //https://www.nuget.org/packages/Aspire.Hosting.Azure.Functions
        private const val AZURE_FUNCTIONS_HOSTING_PACKAGE_NAME = "Aspire.Hosting.Azure.Functions"
        private const val AZURE_FUNCTIONS_HOSTING_PACKAGE_VERSION = "13.1.0"
    }

    override val priority = 1
    override val supportedProjectTypes = listOf(RdProjectType.AzureFunction)

    override suspend fun modifyAppHost(
        appHostEntity: ProjectModelEntity,
        projectEntities: List<ProjectModelEntity>,
        project: Project
    ): List<String> {
        installAzureFunctionsHostingPackage(appHostEntity, project)

        val appHostProjectPath = appHostEntity.url?.toPath()
        if (appHostProjectPath == null) {
            LOG.warn("Unable to find AppHost project path. Skipping Azure Functions nuget installation")
            return emptyList()
        }
        val projectPaths = projectEntities.mapNotNull { it.url?.toPath() }

        return buildList {
            for (projectPath in projectPaths.sorted()) {
                val projectName = projectPath.nameWithoutExtension
                val projectType = projectName.replace('.', '_')
                val projectResourceName = projectName.replace('.', '-').lowercase()

                val lines = buildString {
                    append("builder.AddAzureFunctionsProject<Projects.")
                    append(projectType)
                    append(">(\"")
                    append(projectResourceName)
                    append("\")")
                    appendLine()
                    append("    .WithExternalHttpEndpoints();")
                }
                add(lines)
            }
        }
    }

    private suspend fun installAzureFunctionsHostingPackage(appHostEntity: ProjectModelEntity, project: Project) {
        val appHostProjectId = appHostEntity.getId(project)
        if (appHostProjectId == null) {
            LOG.warn("Unable to find AppHost project id. Skipping Azure Functions hosting nuget installation")
            return
        }

        if (isNuGetPackageInstalled(AZURE_FUNCTIONS_HOSTING_PACKAGE_NAME, appHostProjectId, project) != false) {
            LOG.info("Azure Functions hosting nuget package is already installed")
            return
        }

        val riderNuGetFacade = RiderNuGetFacade.getInstance(project)
        withContext(Dispatchers.EDT) {
            riderNuGetFacade.install(
                RdNuGetProjects(listOf(appHostProjectId)),
                AZURE_FUNCTIONS_HOSTING_PACKAGE_NAME,
                AZURE_FUNCTIONS_HOSTING_PACKAGE_VERSION
            )
        }
    }
}
