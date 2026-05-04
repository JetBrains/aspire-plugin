package com.jetbrains.aspire.worker

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
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
    Unknown
}

@ApiStatus.Internal
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
enum class ResourceStateStyle {
    Success,
    Info,
    Warning,
    Error,
    Unknown
}

@ApiStatus.Internal
enum class ResourceHealthStatus {
    Healthy,
    Unhealthy,
    Degraded
}

@ApiStatus.Internal
enum class ResourceCommandState {
    Enabled,
    Disabled,
    Hidden
}

@ApiStatus.Internal
data class ResourceUrl(
    val endpointName: String?,
    val fullUrl: String,
    val isInternal: Boolean,
    val isInactive: Boolean,
    val sortOrder: Int,
    val displayName: String
)

@ApiStatus.Internal
data class ResourceEnvironmentVariable(
    val key: String,
    val value: String?
)

@ApiStatus.Internal
data class ResourceVolume(
    val source: String,
    val target: String,
    val mountType: String,
    val isReadOnly: Boolean
)

@ApiStatus.Internal
data class ResourceRelationship(
    val resourceName: String,
    val type: String
)

@ApiStatus.Internal
data class ResourceCommand(
    val name: String,
    val displayName: String,
    val confirmationMessage: String?,
    val isHighlighted: Boolean,
    val iconName: String?,
    val displayDescription: String?,
    val state: ResourceCommandState
)
