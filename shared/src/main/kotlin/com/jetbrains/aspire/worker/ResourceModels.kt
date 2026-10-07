package com.jetbrains.aspire.worker

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
enum class ResourceType {
    Project,
    Container,
    Executable,
    Parameter,
    ExternalService,
    MongoDB,
    MySql,
    Postgres,
    SqlServer,
    AzureStorageResource,
    Unknown
}

@ApiStatus.Internal
@Serializable
enum class ResourceLaunchConfigurationType {
    Project,
    AzureFunctions,
    Unknown
}

@ApiStatus.Internal
@Serializable
enum class ResourceState {
    Building,
    Starting,
    Running,
    FailedToStart,
    RuntimeUnhealthy,
    Stopping,
    Exited,
    Finished,
    Waiting,
    NotStarted,
    Hidden,
    Unknown
}

@ApiStatus.Internal
@Serializable
enum class ResourceStateStyle {
    Success,
    Info,
    Warning,
    Error,
    Unknown
}

@ApiStatus.Internal
@Serializable
enum class ResourceHealthStatus {
    Healthy,
    Unhealthy,
    Degraded
}

@ApiStatus.Internal
@Serializable
enum class ResourceCommandState {
    Enabled,
    Disabled,
    Hidden
}

@ApiStatus.Internal
@Serializable
data class ResourceUrl(
    val endpointName: String?,
    val fullUrl: String,
    val isInternal: Boolean,
    val isInactive: Boolean,
    val sortOrder: Int,
    val displayName: String
)

@ApiStatus.Internal
@Serializable
data class ResourceEnvironmentVariable(
    val key: String,
    val value: String?
)

@ApiStatus.Internal
@Serializable
data class ResourceVolume(
    val source: String,
    val target: String,
    val mountType: String,
    val isReadOnly: Boolean
)

@ApiStatus.Internal
@Serializable
data class ResourceRelationship(
    val resourceName: String,
    val type: String
)

@ApiStatus.Internal
@Serializable
data class ResourceCommand(
    val name: String,
    val displayName: String,
    val confirmationMessage: String?,
    val isHighlighted: Boolean,
    val iconName: String?,
    val displayDescription: String?,
    val state: ResourceCommandState
)

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