package com.jetbrains.aspire.worker

import com.google.protobuf.Value
import com.jetbrains.aspire.generated.*
import com.jetbrains.aspire.generated.dashboard.ConsoleLogLine
import com.jetbrains.aspire.generated.dashboard.HealthStatus as ProtoHealthStatus
import com.jetbrains.aspire.generated.dashboard.Resource as ProtoResource
import com.jetbrains.aspire.generated.dashboard.ResourceCommandState as ProtoCommandState
import java.util.*

internal fun ProtoResource.toResourceModel(): ResourceModel = ResourceModel(
    name,
    mapResourceType(resourceType),
    displayName,
    uid,
    if (hasState()) mapResourceState(state) else null,
    if (hasStateStyle()) mapResourceStateStyle(stateStyle) else null,
    if (hasCreatedAt()) createdAt.toDate() else null,
    if (hasStartedAt()) startedAt.toDate() else null,
    if (hasStoppedAt()) stoppedAt.toDate() else null,
    propertiesList.map { it.toResourceProperty() }.toTypedArray(),
    environmentList.map { it.toResourceEnvironmentVariable() }.toTypedArray(),
    urlsList.map { it.toResourceUrl() }.toTypedArray(),
    volumesList.map { it.toResourceVolume() }.toTypedArray(),
    healthReportsList.map { it.toResourceHealthReport() }.toTypedArray(),
    commandsList.map { it.toResourceCommand() }.toTypedArray(),
    relationshipsList.map { it.toResourceRelationship() }.toTypedArray(),
    isHidden
)

internal fun ConsoleLogLine.toResourceLog(): ResourceLog = ResourceLog(
    text,
    if (hasIsStdErr()) isStdErr else false,
    lineNumber
)

private fun mapResourceType(type: String): ResourceType = when (type) {
    "Project" -> ResourceType.Project
    "Container" -> ResourceType.Container
    "Executable" -> ResourceType.Executable
    "Parameter" -> ResourceType.Parameter
    "ExternalService" -> ResourceType.ExternalService
    "MongoDBDatabaseResource" -> ResourceType.MongoDB
    "MySqlDatabaseResource" -> ResourceType.MySql
    "PostgresDatabaseResource" -> ResourceType.Postgres
    "SqlServerDatabaseResource" -> ResourceType.SqlServer
    else -> ResourceType.Unknown
}

private fun mapResourceState(state: String): ResourceState = when (state) {
    "Finished" -> ResourceState.Finished
    "Exited" -> ResourceState.Exited
    "FailedToStart" -> ResourceState.FailedToStart
    "Starting" -> ResourceState.Starting
    "Running" -> ResourceState.Running
    "Hidden" -> ResourceState.Hidden
    else -> ResourceState.Unknown
}

private fun mapResourceStateStyle(style: String): ResourceStateStyle = when (style) {
    "success" -> ResourceStateStyle.Success
    "info" -> ResourceStateStyle.Info
    "warning" -> ResourceStateStyle.Warning
    "error" -> ResourceStateStyle.Error
    else -> ResourceStateStyle.Unknown
}

private fun mapHealthStatus(status: ProtoHealthStatus): ResourceHealthStatus = when (status) {
    ProtoHealthStatus.HEALTH_STATUS_HEALTHY -> ResourceHealthStatus.Healthy
    ProtoHealthStatus.HEALTH_STATUS_UNHEALTHY -> ResourceHealthStatus.Unhealthy
    ProtoHealthStatus.HEALTH_STATUS_DEGRADED -> ResourceHealthStatus.Degraded
    else -> ResourceHealthStatus.Healthy
}

private fun mapCommandState(state: ProtoCommandState): ResourceCommandState = when (state) {
    ProtoCommandState.RESOURCE_COMMAND_STATE_ENABLED -> ResourceCommandState.Enabled
    ProtoCommandState.RESOURCE_COMMAND_STATE_DISABLED -> ResourceCommandState.Disabled
    ProtoCommandState.RESOURCE_COMMAND_STATE_HIDDEN -> ResourceCommandState.Hidden
    else -> ResourceCommandState.Enabled
}

private fun com.jetbrains.aspire.generated.dashboard.ResourceProperty.toResourceProperty(): ResourceProperty =
    ResourceProperty(
        name,
        if (hasDisplayName()) displayName else null,
        getStringValue(value),
        if (hasIsSensitive()) isSensitive else null
    )

private fun getStringValue(value: Value): String? = when {
    value.hasStringValue() -> value.stringValue
    value.hasBoolValue() -> value.boolValue.toString()
    value.hasNumberValue() -> value.numberValue.toBigDecimal().toPlainString()
    value.hasNullValue() -> null
    else -> value.toString()
}

private fun com.jetbrains.aspire.generated.dashboard.EnvironmentVariable.toResourceEnvironmentVariable(): ResourceEnvironmentVariable =
    ResourceEnvironmentVariable(
        name,
        if (hasValue()) value else null
    )

private fun com.jetbrains.aspire.generated.dashboard.Url.toResourceUrl(): ResourceUrl =
    ResourceUrl(
        if (hasEndpointName()) endpointName else null,
        fullUrl,
        isInternal,
        isInactive,
        displayProperties.sortOrder,
        displayProperties.displayName
    )

private fun com.jetbrains.aspire.generated.dashboard.Volume.toResourceVolume(): ResourceVolume =
    ResourceVolume(
        source,
        target,
        mountType,
        isReadOnly
    )

private fun com.jetbrains.aspire.generated.dashboard.HealthReport.toResourceHealthReport(): ResourceHealthReport =
    ResourceHealthReport(
        if (hasStatus()) mapHealthStatus(status) else null,
        key,
        description,
        exception
    )

private fun com.jetbrains.aspire.generated.dashboard.ResourceCommand.toResourceCommand(): ResourceCommand =
    ResourceCommand(
        name,
        displayName,
        if (hasConfirmationMessage()) confirmationMessage else null,
        isHighlighted,
        if (hasIconName()) iconName else null,
        if (hasDisplayDescription()) displayDescription else null,
        mapCommandState(state)
    )

private fun com.jetbrains.aspire.generated.dashboard.ResourceRelationship.toResourceRelationship(): ResourceRelationship =
    ResourceRelationship(
        resourceName,
        type
    )

private fun com.google.protobuf.Timestamp.toDate(): Date =
    Date(seconds * 1000 + nanos / 1_000_000)
