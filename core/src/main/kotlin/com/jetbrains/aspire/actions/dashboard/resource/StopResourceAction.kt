package com.jetbrains.aspire.actions.dashboard.resource

import com.jetbrains.aspire.dashboard.StopResourceCommand
import com.jetbrains.aspire.worker.AspireResourceData

class StopResourceAction : ResourceCommandAction() {
    override fun findCommand(resourceData: AspireResourceData) =
        resourceData.commands.firstOrNull { it.name.equals(StopResourceCommand, true) }
}