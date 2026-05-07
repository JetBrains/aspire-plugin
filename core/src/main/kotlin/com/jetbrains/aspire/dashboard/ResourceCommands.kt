package com.jetbrains.aspire.dashboard

import com.jetbrains.aspire.worker.ResourceCommand

internal const val StartResourceCommand = "start"
internal const val ObsoleteStartResourceCommand = "resource-start"
internal fun List<ResourceCommand>.findStartCommand() = firstOrNull {
    it.name.equals(StartResourceCommand, true) ||
    it.name.equals(ObsoleteStartResourceCommand, true)
}

internal const val StopResourceCommand = "stop"
internal const val ObsoleteStopResourceCommand = "resource-stop"
internal fun List<ResourceCommand>.findStopCommand() = firstOrNull {
    it.name.equals(StopResourceCommand, true) ||
    it.name.equals(ObsoleteStopResourceCommand, true)
}

internal const val RestartResourceCommand = "restart"
internal const val ObsoleteRestartResourceCommand = "resource-restart"
internal fun List<ResourceCommand>.findRestartCommand() = firstOrNull {
    it.name.equals(RestartResourceCommand, true) ||
    it.name.equals(ObsoleteRestartResourceCommand, true)
}

internal const val RebuildResourceCommand = "rebuild"
internal const val ObsoleteRebuildResourceCommand = "resource-rebuild"
internal fun List<ResourceCommand>.findRebuildCommand() = firstOrNull {
    it.name.equals(RebuildResourceCommand, true) ||
    it.name.equals(ObsoleteRebuildResourceCommand, true)
}

internal fun List<ResourceCommand>.hasNonDefaultCommands() = any {
    it.isNonDefault()
}

internal fun List<ResourceCommand>.getNonDefaultCommands() = filter {
    it.isNonDefault()
}

private fun ResourceCommand.isNonDefault() =
    !name.equals(StartResourceCommand, true) &&
    !name.equals(ObsoleteStartResourceCommand, true) &&
    !name.equals(StopResourceCommand, true) &&
    !name.equals(ObsoleteStopResourceCommand, true) &&
    !name.equals(RestartResourceCommand, true) &&
    !name.equals(ObsoleteRestartResourceCommand, true) &&
    !name.equals(RebuildResourceCommand, true) &&
    !name.equals(ObsoleteRebuildResourceCommand, true)