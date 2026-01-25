package com.jetbrains.aspire.actions.dashboard.resource

import com.intellij.openapi.project.Project
import com.jetbrains.aspire.dashboard.AspireResource
import com.jetbrains.aspire.dashboard.RestartResourceCommand
import com.jetbrains.aspire.generated.ResourceType
import com.jetbrains.aspire.sessions.SessionLaunchMode
import com.jetbrains.aspire.sessions.SessionLaunchPreferenceService
import kotlin.io.path.absolutePathString

class RestartWithDebuggerResourceAction : ResourceCommandAction() {
    override fun beforeExecute(resourceService: AspireResource, project: Project) {
        val projectPath = resourceService.data.projectPath?.value ?: return

        SessionLaunchPreferenceService
            .getInstance(project)
            .setPreferredLaunchMode(projectPath.absolutePathString(), SessionLaunchMode.DEBUG)
    }

    override fun checkResourceState(resourceService: AspireResource) =
        resourceService.data.type == ResourceType.Project &&
            resourceService.data.projectPath?.value != null

    override fun findCommand(resource: AspireResource) =
        resource.data.commands.firstOrNull { it.name.equals(RestartResourceCommand, true) }
}