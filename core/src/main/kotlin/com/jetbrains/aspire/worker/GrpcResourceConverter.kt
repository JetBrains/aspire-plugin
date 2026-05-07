package com.jetbrains.aspire.worker

import com.google.protobuf.Timestamp
import com.google.protobuf.Value
import com.jetbrains.aspire.generated.dashboard.Resource
import com.jetbrains.aspire.util.calculateHealthStatus
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.math.roundToInt
import com.jetbrains.aspire.generated.dashboard.ResourceCommandState as GrpcCommandState

internal fun Resource.toAspireResourceData(): AspireResourceData {
    val type = mapResourceType(resourceType)

    val timezone = TimeZone.currentSystemDefault()

    val createdAt = if (hasCreatedAt()) createdAt.toLocalDateTime(timezone) else null
    val startedAt = if (hasStartedAt()) startedAt.toLocalDateTime(timezone) else null
    val stoppedAt = if (hasStoppedAt()) stoppedAt.toLocalDateTime(timezone) else null

    val state = if (hasState()) mapResourceState(state) else null
    val stateStyle = if (hasStateStyle()) mapResourceStateStyle(stateStyle) else null
    val healthStatus = calculateHealthStatus(state, healthReportsList)

    var exitCode: AspireResourceProperty<Int>? = null
    var pid: AspireResourceProperty<Int>? = null
    var projectPath: AspireResourceProperty<Path>? = null
    var executablePath: AspireResourceProperty<Path>? = null
    var executableWorkDir: AspireResourceProperty<Path>? = null
    var args: AspireResourceProperty<String>? = null
    var containerImage: AspireResourceProperty<String>? = null
    var containerId: AspireResourceProperty<String>? = null
    var containerPorts: AspireResourceProperty<String>? = null
    var containerCommand: AspireResourceProperty<String>? = null
    var containerArgs: AspireResourceProperty<String>? = null
    var containerLifetime: AspireResourceProperty<String>? = null
    var connectionString: AspireResourceProperty<String>? = null
    var source: AspireResourceProperty<String>? = null
    var value: AspireResourceProperty<String>? = null

    for (property in propertiesList) {
        val propValue = getStringValue(property.value) ?: continue
        val isSensitive = if (property.hasIsSensitive()) property.isSensitive else false

        when (property.name) {
            "resource.exitCode" -> exitCode = AspireResourceProperty(propValue.toDouble().roundToInt(), isSensitive)
            "executable.pid" -> pid = AspireResourceProperty(propValue.toDouble().roundToInt(), isSensitive)
            "project.path" -> projectPath = AspireResourceProperty(Path(propValue), isSensitive)
            "executable.path" -> executablePath = AspireResourceProperty(Path(propValue), isSensitive)
            "executable.workDir" -> executableWorkDir = AspireResourceProperty(Path(propValue), isSensitive)
            "executable.args" -> args = AspireResourceProperty(propValue, isSensitive)
            "container.image" -> containerImage = AspireResourceProperty(propValue, isSensitive)
            "container.id" -> containerId = AspireResourceProperty(propValue, isSensitive)
            "container.ports" -> containerPorts = AspireResourceProperty(propValue, isSensitive)
            "container.command" -> containerCommand = AspireResourceProperty(propValue, isSensitive)
            "container.args" -> containerArgs = AspireResourceProperty(propValue, isSensitive)
            "container.lifetime" -> containerLifetime = AspireResourceProperty(propValue, isSensitive)
            "resource.connectionString" -> connectionString = AspireResourceProperty(propValue, isSensitive)
            "resource.source" -> source = AspireResourceProperty(propValue, isSensitive)
            "Value" -> value = AspireResourceProperty(propValue, isSensitive)
        }
    }

    val urls = urlsList.map {
        ResourceUrl(
            if (it.hasEndpointName()) it.endpointName else null,
            it.fullUrl,
            it.isInternal,
            it.isInactive,
            it.displayProperties.sortOrder,
            it.displayProperties.displayName
        )
    }

    val environment = environmentList.map {
        ResourceEnvironmentVariable(
            it.name,
            if (it.hasValue()) it.value else null
        )
    }

    val volumes = volumesList.map {
        ResourceVolume(it.source, it.target, it.mountType, it.isReadOnly)
    }

    val relationships = relationshipsList.map {
        ResourceRelationship(it.resourceName, it.type)
    }
    val parentDisplayName = relationshipsList.firstOrNull { it.type.equals("parent", true) }?.resourceName

    val commands = commandsList.map {
        ResourceCommand(
            it.name,
            it.displayName,
            if (it.hasConfirmationMessage()) it.confirmationMessage else null,
            it.isHighlighted,
            if (it.hasIconName()) it.iconName else null,
            if (it.hasDisplayDescription()) it.displayDescription else null,
            mapCommandState(it.state)
        )
    }

    return AspireResourceData(
        uid = uid,
        name = name,
        type = type,
        originType = resourceType,
        displayName = displayName,
        state = state,
        stateStyle = stateStyle,
        isHidden = isHidden,
        healthStatus = healthStatus,
        urls = urls,
        environment = environment,
        volumes = volumes,
        relationships = relationships,
        parentDisplayName = parentDisplayName,
        commands = commands,
        createdAt = createdAt,
        startedAt = startedAt,
        stoppedAt = stoppedAt,
        exitCode = exitCode,
        pid = pid,
        projectPath = projectPath,
        executablePath = executablePath,
        executableWorkDir = executableWorkDir,
        args = args,
        containerImage = containerImage,
        containerId = containerId,
        containerPorts = containerPorts,
        containerCommand = containerCommand,
        containerArgs = containerArgs,
        containerLifetime = containerLifetime,
        connectionString = connectionString,
        source = source,
        value = value
    )
}

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
    "Building" -> ResourceState.Building
    "Starting" -> ResourceState.Starting
    "Running" -> ResourceState.Running
    "FailedToStart" -> ResourceState.FailedToStart
    "RuntimeUnhealthy" -> ResourceState.RuntimeUnhealthy
    "Stopping" -> ResourceState.Stopping
    "Exited" -> ResourceState.Exited
    "Finished" -> ResourceState.Finished
    "Waiting" -> ResourceState.Waiting
    "NotStarted" -> ResourceState.NotStarted
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

private fun mapCommandState(state: GrpcCommandState): ResourceCommandState = when (state) {
    GrpcCommandState.RESOURCE_COMMAND_STATE_ENABLED -> ResourceCommandState.Enabled
    GrpcCommandState.RESOURCE_COMMAND_STATE_DISABLED -> ResourceCommandState.Disabled
    GrpcCommandState.RESOURCE_COMMAND_STATE_HIDDEN -> ResourceCommandState.Hidden
    GrpcCommandState.UNRECOGNIZED -> ResourceCommandState.Disabled
}

private fun getStringValue(value: Value): String? = when {
    value.hasStringValue() -> value.stringValue
    value.hasBoolValue() -> value.boolValue.toString()
    value.hasNumberValue() -> value.numberValue.toString()
    value.hasNullValue() -> null
    value.hasListValue() -> value.listValue.valuesList.joinToString(" ") { getStringValue(it) ?: "" }
    else -> value.toString()
}

private fun Timestamp.toLocalDateTime(timezone: TimeZone): kotlinx.datetime.LocalDateTime =
    Instant.fromEpochSeconds(seconds, nanos).toLocalDateTime(timezone)
