package com.jetbrains.aspire.rider.orchestration

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.workspaceModel.ide.toPath
import com.jetbrains.rider.model.RdProjectType
import com.jetbrains.rider.projectView.workspace.ProjectModelEntity
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.relativeToOrNull

internal class AzureFunctionOrchestrationHandler : BaseOrchestrationHandler() {
    companion object {
        private val LOG = logger<AzureFunctionOrchestrationHandler>()
    }

    override val priority = 1
    override val requiresProjectReference = false
    override val supportedProjectTypes = listOf(RdProjectType.AzureFunction)

    override suspend fun modifyAppHost(
        appHostEntity: ProjectModelEntity,
        projectEntities: List<ProjectModelEntity>,
        project: Project
    ): List<String> {
        val appHostProjectPath = appHostEntity.url?.toPath()
        if (appHostProjectPath == null) {
            LOG.warn("Unable to find AppHost project path. Skipping Azure Functions nuget installation")
            return emptyList()
        }

        installAppHostPackageIfMissing(
            project,
            appHostEntity,
            appHostProjectPath,
            AZURE_FUNCTIONS_HOSTING_PACKAGE_NAME,
            AZURE_FUNCTIONS_HOSTING_PACKAGE_VERSION,
        )

        val projectPaths = projectEntities.mapNotNull { it.url?.toPath() }

        return buildList {
            for (projectPath in projectPaths.sortedBy { it.nameWithoutExtension }) {
                val projectName = projectPath.nameWithoutExtension
                val projectResourceName = projectName.replace('.', '-').lowercase()
                val relativeProjectPath = projectPath.relativeToOrNull(appHostProjectPath.parent)
                if (relativeProjectPath == null) {
                    LOG.warn("Unable to find a path from the AppHost to the Azure Functions project: $projectPath")
                    continue
                }

                val lines = buildString {
                    append("builder.AddAzureFunctionsProject(\"")
                    append(projectResourceName)
                    append("\", \"")
                    append(relativeProjectPath.invariantSeparatorsPathString)
                    append("\")")
                    appendLine()
                    append("    .WithExternalHttpEndpoints();")
                }
                add(lines)
            }
        }
    }
}
