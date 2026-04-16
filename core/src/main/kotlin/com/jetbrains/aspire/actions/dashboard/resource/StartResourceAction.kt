package com.jetbrains.aspire.actions.dashboard.resource

import com.jetbrains.aspire.dashboard.findStartCommand
import com.jetbrains.aspire.worker.AspireResourceData

internal class StartResourceAction : ResourceCommandAction() {
    override fun findCommand(resourceData: AspireResourceData) = resourceData.commands.findStartCommand()
}