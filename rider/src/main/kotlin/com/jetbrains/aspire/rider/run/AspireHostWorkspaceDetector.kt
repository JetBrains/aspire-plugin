package com.jetbrains.aspire.rider.run

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.workspaceModel.ide.toPath
import com.jetbrains.aspire.rider.util.isAspireHostProject
import com.jetbrains.aspire.worker.AppHostDetectionListener
import com.jetbrains.rd.platform.util.idea.LifetimedService
import com.jetbrains.rider.projectView.workspace.ProjectModelEntity
import com.jetbrains.rider.projectView.workspace.WorkspaceModelEvents
import com.jetbrains.rider.projectView.workspace.findProjects
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path

/**
 * Detects Aspire app host projects directly from the Workspace Model and republishes them as
 * [AppHostDetectionListener] events.
 */
@Service(Service.Level.PROJECT)
internal class AspireHostWorkspaceDetector(private val project: Project) : LifetimedService() {
    companion object {
        fun getInstance(project: Project): AspireHostWorkspaceDetector = project.service()
    }

    private val knownHosts = mutableSetOf<Path>()

    init {
        val events = WorkspaceModelEvents.getInstance(project)

        events.addSignal.advise(serviceLifetime) { event ->
            if (event.entity.isAspireHostProject()) add(event.entity)
        }

        events.removeSignal.advise(serviceLifetime) { event ->
            if (event.entity.isAspireHostProject()) remove(event.entity)
        }

        events.updateSignal.advise(serviceLifetime) { event ->
            val wasHost = event.oldEntity.isAspireHostProject()
            val isHost = event.newEntity.isAspireHostProject()
            when {
                wasHost && !isHost -> remove(event.oldEntity)
                !wasHost && isHost -> add(event.newEntity)
                wasHost && isHost && event.oldEntity.url?.toPath() != event.newEntity.url?.toPath() -> {
                    remove(event.oldEntity)
                    add(event.newEntity)
                }
            }
        }
    }

    /**
     * Reports app hosts that are already present when the solution is loaded.
     * [knownHosts] deduplicates against the initial [WorkspaceModelEvents.addSignal] events.
     */
    suspend fun scanExisting() {
        val hosts = project.serviceAsync<WorkspaceModel>().findProjects().filter { it.isAspireHostProject() }
        withContext(Dispatchers.EDT) {
            hosts.forEach(::add)
        }
    }

    private fun add(entity: ProjectModelEntity) {
        val path = entity.url?.toPath() ?: return
        if (!knownHosts.add(path)) return
        project.messageBus
            .syncPublisher(AppHostDetectionListener.TOPIC)
            .appHostDetected(entity.name, path)
    }

    private fun remove(entity: ProjectModelEntity) {
        val path = entity.url?.toPath() ?: return
        if (!knownHosts.remove(path)) return
        project.messageBus
            .syncPublisher(AppHostDetectionListener.TOPIC)
            .appHostRemoved(path)
    }
}
