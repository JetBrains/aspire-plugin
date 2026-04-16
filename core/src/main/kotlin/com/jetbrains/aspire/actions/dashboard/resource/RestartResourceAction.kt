package com.jetbrains.aspire.actions.dashboard.resource

import com.jetbrains.aspire.dashboard.findRestartCommand
import com.jetbrains.aspire.generated.ResourceType
import com.jetbrains.aspire.worker.AspireResourceData

internal class RestartResourceAction : ResourceCommandAction() {
    override fun checkResourceState(resourceData: AspireResourceData) =
        resourceData.type != ResourceType.Project || resourceData.projectPath?.value == null

    override fun findCommand(resourceData: AspireResourceData) = resourceData.commands.findRestartCommand()
}