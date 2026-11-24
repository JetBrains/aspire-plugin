package com.jetbrains.aspire.rider.orchestration

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.workspaceModel.ide.toPath
import com.jetbrains.rider.model.RdProjectType
import com.jetbrains.rider.projectView.workspace.ProjectModelEntity
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.relativeToOrNull

internal class DotnetProjectOrchestrationHandler : BaseOrchestrationHandler() {
    companion object {
        private val LOG = logger<DotnetProjectOrchestrationHandler>()
    }

    override val priority = 0
    override val requiresProjectReference = false
    override val supportedProjectTypes = listOf(
        RdProjectType.Default,
        RdProjectType.Web,
        RdProjectType.XamlProject,
        RdProjectType.Wpf
    )

    override suspend fun modifyAppHost(
        appHostEntity: ProjectModelEntity,
        projectEntities: List<ProjectModelEntity>,
        project: Project
    ): List<String> {
        val appHostProjectPath = appHostEntity.url?.toPath()
        if (appHostProjectPath == null) {
            LOG.warn("Unable to find AppHost project path. Skipping .NET project registration")
            return emptyList()
        }

        installAppHostPackageIfMissing(
            project,
            appHostEntity,
            appHostProjectPath,
            DOTNET_HOSTING_PACKAGE_NAME,
            DOTNET_HOSTING_PACKAGE_VERSION,
        )

        val projectPaths = projectEntities.mapNotNull { it.url?.toPath() }

        return buildList {
            for (projectPath in projectPaths.sortedBy { it.nameWithoutExtension }) {
                val projectName = projectPath.nameWithoutExtension
                val projectResourceName = projectName.replace('.', '-').lowercase()
                val relativeProjectPath = projectPath.relativeToOrNull(appHostProjectPath.parent)
                if (relativeProjectPath == null) {
                    LOG.warn("Unable to find a path from the AppHost to the .NET project: $projectPath")
                    continue
                }

                val line = buildString {
                    append("builder.AddDotnetProject(\"")
                    append(projectResourceName)
                    append("\", \"")
                    append(relativeProjectPath.invariantSeparatorsPathString)
                    append("\");")
                }
                add(line)
            }
        }
    }
}
