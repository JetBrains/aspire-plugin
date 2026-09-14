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
