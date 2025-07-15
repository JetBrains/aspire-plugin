package com.jetbrains.rider.aspire.projectTemplates

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.virtualFile
import com.jetbrains.rd.ide.model.RdPostProcessParameters
import com.jetbrains.rider.aspire.AspireBundle
import com.jetbrains.rider.aspire.util.isAspireHost
import com.jetbrains.rider.aspire.util.isAspireSharedProject
import com.jetbrains.rider.model.AddProjectCommand
import com.jetbrains.rider.model.projectModelTasks
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.projectView.workspace.findProjects
import com.jetbrains.rider.projectView.workspace.getId
import com.jetbrains.rider.projectView.workspace.getSolutionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.absolutePathString

@Service(Service.Level.PROJECT)
class AspireOrchestrationSupportService(private val project: Project) {
    companion object {
        fun getInstance(project: Project): AspireOrchestrationSupportService = project.service()
        private val LOG = logger<AspireOrchestrationSupportService>()
    }

    suspend fun addAspireOrchestrationSupport() {
        var (hostProjectPath, sharedProjectPath) = findExistingAspireProjects()

        val needToGenerateAppHost = hostProjectPath == null
        val needToGenerateServiceDefaults = sharedProjectPath == null

        if (needToGenerateAppHost || needToGenerateServiceDefaults) {
            val generatedProjects =
                generateAspireProjects(needToGenerateAppHost, needToGenerateServiceDefaults) ?: return

            val (generatedHostProjectPath, generatedSharedProjectPath) = generatedProjects
            if (generatedHostProjectPath != null) hostProjectPath = generatedHostProjectPath
            if (generatedSharedProjectPath != null) sharedProjectPath = generatedSharedProjectPath
        }
    }

    private suspend fun findExistingAspireProjects(): Pair<Path?, Path?> {
        var hostProjectPath: Path? = null
        var sharedProjectPath: Path? = null

        val dotnetProjects = project.serviceAsync<WorkspaceModel>().findProjects()

        for (dotnetProject in dotnetProjects) {
            if (dotnetProject.isAspireHost()) {
                val projectFile = dotnetProject.url?.virtualFile?.toNioPath()
                if (projectFile == null) {
                    LOG.warn("Unable to find a virtual file for the Aspire AppHost")
                    continue
                }
                hostProjectPath = projectFile
            } else if (dotnetProject.isAspireSharedProject()) {
                val projectFile = dotnetProject.url?.virtualFile?.toNioPath()
                if (projectFile == null) {
                    LOG.warn("Unable to find a virtual file for the Aspire SharedProject")
                    continue
                }
                sharedProjectPath = projectFile
            }
        }

        return hostProjectPath to sharedProjectPath
    }

    private suspend fun generateAspireProjects(
        needToGenerateAppHost: Boolean,
        needToGenerateServiceDefaults: Boolean
    ): Pair<Path?, Path?>? {
        suspend fun notifyAboutFailedGeneration() = withContext(Dispatchers.EDT) {
            Notification(
                "Aspire",
                AspireBundle.message("notification.unable.to.generate.aspire.projects"),
                "",
                NotificationType.ERROR
            )
                .notify(project)
        }

        val solutionId = project.serviceAsync<WorkspaceModel>().getSolutionEntity()?.getId(project)
        if (solutionId == null) {
            LOG.warn("Unable to find a solution for the .NET Aspire project generation")
            notifyAboutFailedGeneration()
            return null
        }

        val (generatedHostProjectPath, generatedSharedProjectPath) = AspireProjectTemplateGenerator
            .getInstance(project)
            .generateAspireProjectsFromTemplates(
                needToGenerateAppHost,
                needToGenerateServiceDefaults
            )

        if (needToGenerateAppHost && generatedHostProjectPath == null || needToGenerateServiceDefaults && generatedSharedProjectPath == null) {
            LOG.warn("Some of the requested projects were not generated")
            notifyAboutFailedGeneration()
            return null
        }

        addGeneratedProjectToSolution(solutionId, generatedHostProjectPath, generatedSharedProjectPath)

        return generatedHostProjectPath to generatedSharedProjectPath
    }

    private suspend fun addGeneratedProjectToSolution(
        solutionId: Int,
        generatedHostProjectPath: Path?,
        generatedSharedProjectPath: Path?
    ) {
        val projects = buildList {
            generatedHostProjectPath?.let { add(it.absolutePathString()) }
            generatedSharedProjectPath?.let { add(it.absolutePathString()) }
        }
        val parameters = RdPostProcessParameters(false, listOf())

        val command = AddProjectCommand(
            solutionId,
            projects,
            emptyList(),
            true,
            parameters,
        )

        withContext(Dispatchers.EDT) {
            project.solution.projectModelTasks.addProject.startSuspending(command)
        }
    }
}