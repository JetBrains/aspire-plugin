package com.jetbrains.aspire.actions.dashboard.resource

import com.jetbrains.aspire.worker.AspireResource
import com.jetbrains.aspire.dashboard.StopResourceCommand

class StopResourceAction : ResourceCommandAction() {
    override fun findCommand(resource: AspireResource) =
        resource.data.commands.firstOrNull { it.name.equals(StopResourceCommand, true) }
}