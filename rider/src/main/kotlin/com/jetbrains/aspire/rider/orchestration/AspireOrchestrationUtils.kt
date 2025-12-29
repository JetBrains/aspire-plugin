package com.jetbrains.aspire.rider.orchestration

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.virtualFile
import com.jetbrains.aspire.rider.generated.ReferenceProjectsFromAppHostRequest
import com.jetbrains.aspire.rider.generated.ReferenceProjectsFromAppHostResponse
import com.jetbrains.aspire.rider.generated.ReferenceServiceDefaultsFromProjectsRequest
import com.jetbrains.aspire.rider.generated.ReferenceServiceDefaultsFromProjectsResponse
import com.jetbrains.aspire.rider.generated.aspirePluginModel
import com.jetbrains.aspire.util.isAspireHostProject
import com.jetbrains.aspire.util.isAspireSharedProject
import com.jetbrains.rd.ide.model.RdPostProcessParameters
import com.jetbrains.rider.ijent.extensions.toRd
import com.jetbrains.rider.model.AddProjectCommand
import com.jetbrains.rider.model.RdProjectDescriptor
import com.jetbrains.rider.model.RdProjectType
import com.jetbrains.rider.model.projectModelTasks
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.projectView.workspace.ProjectModelEntity
import com.jetbrains.rider.projectView.workspace.getId
import com.jetbrains.rider.projectView.workspace.getSolutionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path

private val supportedProjectTypes = listOf(
    RdProjectType.Default,
    RdProjectType.Web,
    RdProjectType.XamlProject,
    RdProjectType.MAUI
)

internal fun ProjectModelEntity.isAspireOrchestrationSupported(): Boolean {
    if (isAspireHostProject() || isAspireSharedProject()) return false

    val entityDescriptor = descriptor
    if (entityDescriptor !is RdProjectDescriptor) return false

    val extension = url?.virtualFile?.extension ?: return false
    if (extension != "csproj" && extension != "fsproj") return false

    val type = entityDescriptor.specificType
    if (!supportedProjectTypes.contains(type)) return false

    return entityDescriptor.isDotNetCore
}

internal suspend fun findSolutionId(project: Project): Int? {
    return project.serviceAsync<WorkspaceModel>().getSolutionEntity()?.getId(project)
}

internal suspend fun addProjectToSolution(project: Project, solutionId: Int, projectPath: Path) {
    val parameters = RdPostProcessParameters(false, listOf())
    val command = AddProjectCommand(
        solutionId,
        listOf(projectPath.toRd()),
        emptyList(),
        true,
        parameters,
    )

    withContext(Dispatchers.EDT) {
        project.solution.projectModelTasks.addProject.startSuspending(command)
    }
}

internal suspend fun referenceByHostProject(
    project: Project,
    hostProjectPath: Path,
    projectFilePaths: List<Path>
): ReferenceProjectsFromAppHostResponse? = withContext(Dispatchers.EDT) {
    val request = ReferenceProjectsFromAppHostRequest(
        hostProjectPath.toRd(),
        projectFilePaths.map { it.toRd() }
    )

    project.solution.aspirePluginModel.referenceProjectsFromAppHost.startSuspending(request)
}

internal suspend fun referenceSharedProject(
    project: Project,
    sharedProjectPath: Path,
    projectFilePaths: List<Path>
): ReferenceServiceDefaultsFromProjectsResponse? = withContext(Dispatchers.EDT) {
    val request = ReferenceServiceDefaultsFromProjectsRequest(
        sharedProjectPath.toRd(),
        projectFilePaths.map { it.toRd() }
    )

    project.solution.aspirePluginModel.referenceServiceDefaultsFromProjects.startSuspending(request)
}