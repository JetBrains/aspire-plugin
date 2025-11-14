package com.jetbrains.rider.aspire.actions.dashboard.resource

import com.jetbrains.rider.aspire.dashboard.AspireResource
import com.jetbrains.rider.aspire.dashboard.StopResourceCommand

class StopResourceAction : ResourceCommandAction() {
    override fun findCommand(resource: AspireResource) =
        resource.commands.firstOrNull { it.name.equals(StopResourceCommand, true) }
}