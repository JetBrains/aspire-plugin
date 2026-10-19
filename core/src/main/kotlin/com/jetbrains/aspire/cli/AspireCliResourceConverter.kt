package com.jetbrains.aspire.cli

import com.jetbrains.aspire.resources.AspireResourceChange
import com.jetbrains.aspire.resources.AspireResourceUpdate
import com.jetbrains.aspire.worker.AspireAppHostPath
import com.jetbrains.aspire.worker.AspirePath
import com.jetbrains.aspire.worker.AspireResourceData
import com.jetbrains.aspire.worker.AspireResourceId
import com.jetbrains.aspire.worker.AspireResourceProperty
import com.jetbrains.aspire.worker.ResourceCommand
import com.jetbrains.aspire.worker.ResourceCommandState
import com.jetbrains.aspire.worker.ResourceHealthStatus
import com.jetbrains.aspire.worker.ResourceLaunchConfigurationType
import com.jetbrains.aspire.worker.ResourceState
import com.jetbrains.aspire.worker.ResourceStateStyle
import com.jetbrains.aspire.worker.ResourceType
import com.jetbrains.aspire.worker.ResourceUrl
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun AspireCliResource.toAspireResourceUpdate(appHostPath: AspireAppHostPath): AspireResourceUpdate {
    val data = toAspireResourceData(appHostPath)
    return AspireResourceUpdate.Changes(listOf(AspireResourceChange.Upsert(data)))
}

private fun AspireCliResource.toAspireResourceData(appHostPath: AspireAppHostPath): AspireResourceData {
    val resourceState = state?.let { value -> ResourceState.entries.find { it.name == value } ?: ResourceState.Unknown }
    return AspireResourceData(
        id = AspireResourceId(appHostPath, name),
        uid = name,
        name = name,
        type = mapResourceType(resourceType),
        launchConfigurationType = when (property("resource.launchConfigurationType")?.value) {
            "project" -> ResourceLaunchConfigurationType.Project
            "azure-functions" -> ResourceLaunchConfigurationType.AzureFunctions
            else -> ResourceLaunchConfigurationType.Unknown
        },
        originType = resourceType,
        displayName = displayName ?: name,
        state = resourceState,
        stateStyle = stateStyle?.let { value ->
            ResourceStateStyle.entries.find { it.name.equals(value, true) } ?: ResourceStateStyle.Unknown
        },
        isHidden = resourceState == ResourceState.Hidden,
        healthStatus = calculateHealthStatus(resourceState),
        urls = urls.orEmpty().map {
            ResourceUrl(
                endpointName = it.name,
                fullUrl = it.url,
                isInternal = it.isInternal,
                isInactive = false,
                sortOrder = 0,
                displayName = it.displayName ?: it.name.orEmpty(),
            )
        },
        environment = emptyList(),
        volumes = emptyList(),
        relationships = emptyList(),
        parentDisplayName = null,
        commands = commands.orEmpty().map { (commandName, command) ->
            ResourceCommand(
                name = commandName,
                displayName = command.displayName ?: commandName,
                confirmationMessage = null,
                isHighlighted = command.visibility == "Highlighted",
                iconName = null,
                displayDescription = command.description,
                state = if (command.visibility == "Hidden") ResourceCommandState.Hidden else {
                    ResourceCommandState.entries.find { it.name == command.state } ?: ResourceCommandState.Disabled
                },
            )
        },
        createdAt = null,
        startedAt = null,
        stoppedAt = null,
        exitCode = (exitCode ?: property("resource.exitCode")?.value?.toIntOrNull())
            ?.let { AspireResourceProperty(it, false) },
        pid = property("executable.pid")?.value?.toIntOrNull()
            ?.let { AspireResourceProperty(it, false) },
        projectPath = property("project.path")
            ?.let { AspireResourceProperty(AspirePath(it.value), it.isSensitive) },
        executablePath = property("executable.path")
            ?.let { AspireResourceProperty(AspirePath(it.value), it.isSensitive) },
        executableWorkDir = property("executable.workDir")
            ?.let { AspireResourceProperty(AspirePath(it.value), it.isSensitive) },
        args = property("executable.args"),
        containerImage = property("container.image"),
        containerId = property("container.id"),
        containerPorts = property("container.ports"),
        containerCommand = property("container.command"),
        containerArgs = property("container.args"),
        containerLifetime = property("container.lifetime"),
        connectionString = property("resource.connectionString"),
        source = source
            ?.let { AspireResourceProperty(it, false) } ?: property("resource.source"),
        value = property("Value"),
    )
}

private fun AspireCliResource.property(name: String): AspireResourceProperty<String>? =
    properties?.get(name)?.stringValue()?.let { AspireResourceProperty(it, false) }

private fun JsonElement.stringValue(): String? = when (this) {
    is JsonPrimitive -> contentOrNull
    is JsonArray -> joinToString(" ") { it.stringValue().orEmpty() }
    else -> toString()
}

private fun AspireCliResource.calculateHealthStatus(state: ResourceState?): ResourceHealthStatus? {
    if (state != ResourceState.Running) return null
    ResourceHealthStatus.entries.find { it.name == healthStatus }?.let { return it }
    val statuses = healthReports.orEmpty().values.map { it.status }
    return when {
        statuses.any { it == null || it == "Unhealthy" } -> ResourceHealthStatus.Unhealthy
        statuses.any { it == "Degraded" } -> ResourceHealthStatus.Degraded
        else -> ResourceHealthStatus.Healthy
    }
}

private fun mapResourceType(type: String): ResourceType = when (type) {
    "MongoDBDatabaseResource" -> ResourceType.MongoDB
    "MySqlDatabaseResource" -> ResourceType.MySql
    "PostgresDatabaseResource" -> ResourceType.Postgres
    "SqlServerDatabaseResource" -> ResourceType.SqlServer
    else -> ResourceType.entries.find { it.name == type } ?: ResourceType.Unknown
}