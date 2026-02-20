package com.jetbrains.aspire.actions.dashboard.resource

import com.jetbrains.aspire.dashboard.StartResourceCommand
import com.jetbrains.aspire.worker.AspireResourceData

class StartResourceAction : ResourceCommandAction() {
    override fun findCommand(resourceData: AspireResourceData) =
        resourceData.commands.firstOrNull { it.name.equals(StartResourceCommand, true) }
}