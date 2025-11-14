package com.jetbrains.rider.aspire.actions.dashboard.resource

import com.jetbrains.rider.aspire.dashboard.AspireResource
import com.jetbrains.rider.aspire.dashboard.StartResourceCommand

class StartResourceAction : ResourceCommandAction() {
    override fun findCommand(resource: AspireResource) =
        resource.commands.firstOrNull { it.name.equals(StartResourceCommand, true) }
}