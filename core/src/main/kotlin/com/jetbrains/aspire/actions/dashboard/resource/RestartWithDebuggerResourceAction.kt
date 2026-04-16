package com.jetbrains.aspire.actions.dashboard.resource

import com.intellij.openapi.project.Project
import com.jetbrains.aspire.dashboard.findRestartCommand
import com.jetbrains.aspire.generated.ResourceType
import com.jetbrains.aspire.sessions.SessionLaunchMode
import com.jetbrains.aspire.sessions.SessionLaunchPreferenceService
import com.jetbrains.aspire.worker.AspireResourceData
import kotlin.io.path.absolutePathString

internal class RestartWithDebuggerResourceAction : ResourceCommandAction() {
    override fun beforeExecute(resourceData: AspireResourceData, project: Project) {
        val projectPath = resourceData.projectPath?.value ?: return

        SessionLaunchPreferenceService
            .getInstance(project)
            .setPreferredLaunchMode(projectPath.absolutePathString(), SessionLaunchMode.DEBUG)
    }

    override fun checkResourceState(resourceData: AspireResourceData) =
        resourceData.type == ResourceType.Project && resourceData.projectPath?.value != null

    override fun findCommand(resourceData: AspireResourceData) = resourceData.commands.findRestartCommand()
}