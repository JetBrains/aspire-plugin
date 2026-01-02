package com.jetbrains.aspire.rider.orchestration

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.psi.PsiDocumentManager
import com.jetbrains.aspire.rider.generated.*
import com.jetbrains.aspire.util.isAspireHostProject
import com.jetbrains.aspire.util.isAspireSharedProject
import com.jetbrains.rd.ide.model.RdPostProcessParameters
import com.jetbrains.rd.platform.util.TimeoutTracker
import com.jetbrains.rd.util.reactive.hasTrueValue
import com.jetbrains.rider.ijent.extensions.toRd
import com.jetbrains.rider.model.*
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.projectView.workspace.ProjectModelEntity
import com.jetbrains.rider.projectView.workspace.getId
import com.jetbrains.rider.projectView.workspace.getSolutionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.file.Path
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

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

internal suspend fun addProjectToSolution(
    project: Project,
    solutionId: Int,
    projectPath: Path,
    logger: Logger
): Result<Unit> {
    try {
        //Should use `RiderProjectModelWaiter.waitForProjectModelReadySuspending` but there are threading issues there
        waitForProjectModelIsReady(project)
    } catch (ce: CancellationException) {
        throw ce
    } catch (e: Exception) {
        logger.warn("Exception during waiting for project model ready", e)
        return Result.failure(e)
    }

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

    return Result.success(Unit)
}

private suspend fun waitForProjectModelIsReady(project: Project) {
    val timeoutTracker = TimeoutTracker(60.seconds)
    withContext(Dispatchers.EDT) {
        val riderSolutionLifecycle = project.solution.riderSolutionLifecycle
        val isProjectModelReady = riderSolutionLifecycle.isProjectModelReady
        while (!isProjectModelReady.hasTrueValue) {
            if (timeoutTracker.isExpired) {
                val projectModelMonitorState = withTimeoutOrNull(1.seconds) {
                    project.solution.projectModelTasks.getProjectModelMonitorState.startSuspending(Unit)
                } ?: "can't record project model state, probably backend is hang"

                throw Exception("Project model wasn't ready in time: $projectModelMonitorState.")
            }

            PsiDocumentManager.getInstance(project).commitAllDocuments()
            delay(10)
        }
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