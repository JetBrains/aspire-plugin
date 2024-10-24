package com.jetbrains.rider.aspire.actions.dashboard

import com.jetbrains.rider.aspire.services.AspireResource
import com.jetbrains.rider.aspire.services.RestartResourceCommand

class RestartResourceAction : ResourceCommandAction() {
    override fun findCommand(resource: AspireResource) =
        resource.commands.firstOrNull { it.commandType.equals(RestartResourceCommand, true) }
}