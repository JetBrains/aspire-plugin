package com.jetbrains.aspire.actions.dashboard.resource

import com.jetbrains.aspire.dashboard.findRebuildCommand
import com.jetbrains.aspire.worker.AspireResourceData

internal class RebuildResourceAction : ResourceCommandAction() {
    override fun findCommand(resourceData: AspireResourceData) = resourceData.commands.findRebuildCommand()
}