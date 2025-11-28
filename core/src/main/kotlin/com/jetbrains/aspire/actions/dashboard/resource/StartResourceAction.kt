package com.jetbrains.aspire.actions.dashboard.resource

import com.jetbrains.aspire.dashboard.AspireResource
import com.jetbrains.aspire.dashboard.StartResourceCommand

class StartResourceAction : ResourceCommandAction() {
    override fun findCommand(resource: AspireResource) =
        resource.commands.firstOrNull { it.name.equals(StartResourceCommand, true) }
}