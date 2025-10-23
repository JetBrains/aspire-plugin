package com.jetbrains.rider.aspire.actions.dashboard.resource

import com.jetbrains.rider.aspire.dashboard.AspireResource
import com.jetbrains.rider.aspire.dashboard.RestartResourceCommand
import com.jetbrains.rider.aspire.generated.ResourceType

class RestartResourceAction : ResourceCommandAction() {
    override fun checkResourceState(resourceService: AspireResource) =
        resourceService.type != ResourceType.Project ||
                resourceService.projectPath?.value == null

    override fun findCommand(resource: AspireResource) =
        resource.commands.firstOrNull { it.name.equals(RestartResourceCommand, true) }
}