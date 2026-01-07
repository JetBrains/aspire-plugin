package com.jetbrains.aspire.rider.orchestration

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.virtualFile
import com.jetbrains.rider.model.RdProjectType
import com.jetbrains.rider.projectView.workspace.ProjectModelEntity
import kotlin.io.path.nameWithoutExtension

internal class DefaultProjectOrchestrationHandler : BaseOrchestrationHandler() {
    companion object {
        private val LOG = logger<DefaultProjectOrchestrationHandler>()
    }

    override val priority = 0
    override val supportedProjectTypes = listOf(RdProjectType.Default, RdProjectType.Web, RdProjectType.XamlProject)

    override suspend fun modifyAppHost(
        appHostEntity: ProjectModelEntity,
        projectEntities: List<ProjectModelEntity>,
        project: Project
    ): List<String> {
        val projectPaths = projectEntities.mapNotNull { it.url?.virtualFile?.toNioPath() }
        val projectNames = projectPaths.map { it.nameWithoutExtension }

        return buildList {
            for (projectName in projectNames.sorted()) {
                val projectType = projectName.replace('.', '_')
                val projectResourceName = projectName.replace('.', '-').lowercase()

                val line = buildString {
                    append("builder.AddProject<Projects.")
                    append(projectType)
                    append(">(\"")
                    append(projectResourceName)
                    append("\");")
                }
                add(line)
            }
        }
    }

}