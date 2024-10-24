package com.jetbrains.rider.aspire.actions.dashboard.resource

import com.jetbrains.rider.aspire.services.AspireResource
import com.jetbrains.rider.aspire.services.StopResourceCommand

class StopResourceAction : ResourceCommandAction() {
    override fun findCommand(resource: AspireResource) =
        resource.commands.firstOrNull { it.commandType.equals(StopResourceCommand, true) }
}