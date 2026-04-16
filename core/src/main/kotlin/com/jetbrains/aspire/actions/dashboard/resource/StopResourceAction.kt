package com.jetbrains.aspire.actions.dashboard.resource

import com.jetbrains.aspire.dashboard.findStopCommand
import com.jetbrains.aspire.worker.AspireResourceData

internal class StopResourceAction : ResourceCommandAction() {
    override fun findCommand(resourceData: AspireResourceData) = resourceData.commands.findStopCommand()
}