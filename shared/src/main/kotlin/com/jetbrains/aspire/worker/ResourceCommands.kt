package com.jetbrains.aspire.worker

import org.jetbrains.annotations.ApiStatus

private const val StartResourceCommand = "start"
private const val ObsoleteStartResourceCommand = "resource-start"

@ApiStatus.Internal
fun List<ResourceCommand>.findStartCommand() = firstOrNull {
    it.name.equals(StartResourceCommand, true) ||
    it.name.equals(ObsoleteStartResourceCommand, true)
}

private const val StopResourceCommand = "stop"
private const val ObsoleteStopResourceCommand = "resource-stop"

@ApiStatus.Internal
fun List<ResourceCommand>.findStopCommand() = firstOrNull {
    it.name.equals(StopResourceCommand, true) ||
    it.name.equals(ObsoleteStopResourceCommand, true)
}

private const val RestartResourceCommand = "restart"
private const val ObsoleteRestartResourceCommand = "resource-restart"

@ApiStatus.Internal
fun List<ResourceCommand>.findRestartCommand() = firstOrNull {
    it.name.equals(RestartResourceCommand, true) ||
    it.name.equals(ObsoleteRestartResourceCommand, true)
}

private const val RebuildResourceCommand = "rebuild"
private const val ObsoleteRebuildResourceCommand = "resource-rebuild"

@ApiStatus.Internal
fun List<ResourceCommand>.findRebuildCommand() = firstOrNull {
    it.name.equals(RebuildResourceCommand, true) ||
    it.name.equals(ObsoleteRebuildResourceCommand, true)
}

@ApiStatus.Internal
fun List<ResourceCommand>.hasNonDefaultCommands() = any {
    it.isNonDefault()
}

@ApiStatus.Internal
fun List<ResourceCommand>.getNonDefaultCommands() = filter {
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
