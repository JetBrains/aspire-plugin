package com.jetbrains.aspire.actions.dashboard.resource

import com.jetbrains.aspire.dashboard.AspireResource
import com.jetbrains.aspire.dashboard.RestartResourceCommand
import com.jetbrains.aspire.generated.ResourceType

class RestartResourceAction : ResourceCommandAction() {
    override fun checkResourceState(resourceService: AspireResource) =
        resourceService.data.type != ResourceType.Project ||
                resourceService.data.projectPath?.value == null

    override fun findCommand(resource: AspireResource) =
        resource.data.commands.firstOrNull { it.name.equals(RestartResourceCommand, true) }
}