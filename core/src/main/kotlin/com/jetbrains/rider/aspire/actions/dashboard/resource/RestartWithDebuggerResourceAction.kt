package com.jetbrains.rider.aspire.actions.dashboard.resource

import com.intellij.openapi.project.Project
import com.jetbrains.rider.aspire.dashboard.AspireResource
import com.jetbrains.rider.aspire.dashboard.RestartResourceCommand
import com.jetbrains.rider.aspire.generated.ResourceType
import com.jetbrains.rider.aspire.sessions.SessionLaunchMode
import com.jetbrains.rider.aspire.sessions.SessionLaunchPreferenceService
import kotlin.io.path.absolutePathString

class RestartWithDebuggerResourceAction : ResourceCommandAction() {
    override fun beforeExecute(resourceService: AspireResource, project: Project) {
        val projectPath = resourceService.projectPath?.value ?: return

        SessionLaunchPreferenceService
            .getInstance(project)
            .setPreferredLaunchMode(projectPath.absolutePathString(), SessionLaunchMode.DEBUG)
    }

    override fun checkResourceState(resourceService: AspireResource) =
        resourceService.type == ResourceType.Project &&
            resourceService.projectPath?.value != null

    override fun findCommand(resource: AspireResource) =
        resource.commands.firstOrNull { it.name.equals(RestartResourceCommand, true) }
}