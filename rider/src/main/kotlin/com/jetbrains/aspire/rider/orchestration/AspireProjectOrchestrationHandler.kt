package com.jetbrains.aspire.rider.orchestration

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.jetbrains.rider.model.RdProjectType
import com.jetbrains.rider.projectView.workspace.ProjectModelEntity

interface AspireProjectOrchestrationHandler {
    companion object {
        private val EP_NAME =
            ExtensionPointName<AspireProjectOrchestrationHandler>("com.jetbrains.aspire.projectOrchestrationHandler")

        fun getHandlerForType(type: RdProjectType): AspireProjectOrchestrationHandler? {
            return EP_NAME.extensionList
                .sortedBy { it.priority }
                .firstOrNull { it.supportedProjectTypes.contains(type) }
        }
    }

    val priority: Int
    val supportedProjectTypes: List<RdProjectType>

    /**
     * Provide [supportedProjectTypes] specific modifications to the `AppHost` project.
     *
     * This method should modify the `AppHost` project specific to the [supportedProjectTypes] (e.g., add some nuget packages).
     * It also should return the lines of code that should be added to the `AppHost` main file (e.g. `AppProject<T>()).
     */
    suspend fun modifyAppHost(
        appHostEntity: ProjectModelEntity,
        projectEntities: List<ProjectModelEntity>,
        project: Project
    ): List<String>

    /**
     * Generates `ServiceDefaults` project and applies modifications to the associated projects.
     *
     * This method should generate the `ServiceDefaults` project specific to the [supportedProjectTypes],
     * the add reference to the generated project to the provided [projectEntities]
     * and also do some code modifications (e.g., add some default methods like `AddServiceDefaults()` and `MapDefaultEndpoints()`).
     *
     * @param projectEntities A list of project model entities that should be modified with generated `ServiceDefaults` project.
     * @return A boolean indicating whether the operation was successful.
     */
    suspend fun generateServiceDefaultsAndModifyProjects(
        projectEntities: List<ProjectModelEntity>,
        project: Project,
    ): Boolean
}