package com.jetbrains.aspire.actions.dashboard.resource

import com.jetbrains.aspire.worker.findRestartCommand
import com.jetbrains.aspire.worker.ResourceKind
import com.jetbrains.aspire.worker.AspireResourceData

internal class RestartResourceAction : ResourceCommandAction() {
    override fun checkResourceState(resourceData: AspireResourceData) =
        resourceData.kind != ResourceKind.Project || resourceData.projectPath?.value == null

    override fun findCommand(resourceData: AspireResourceData) = resourceData.commands.findRestartCommand()
}
