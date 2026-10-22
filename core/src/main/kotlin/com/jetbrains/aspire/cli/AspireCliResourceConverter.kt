package com.jetbrains.aspire.cli

import com.jetbrains.aspire.resources.AspireResourceChange
import com.jetbrains.aspire.resources.AspireResourceUpdate
import com.jetbrains.aspire.util.mapLaunchConfigurationType
import com.jetbrains.aspire.util.mapResourceState
import com.jetbrains.aspire.util.mapResourceStateStyle
import com.jetbrains.aspire.util.mapResourceType
import com.jetbrains.aspire.worker.*
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.jetbrains.annotations.ApiStatus
import kotlin.time.Instant

@ApiStatus.Internal
fun AspireCliResource.toAspireResourceUpdate(appHostPath: AspireAppHostPath): AspireResourceUpdate {
    val data = toAspireResourceData(appHostPath)
    return AspireResourceUpdate.Changes(listOf(AspireResourceChange.Upsert(data)))
}

private fun AspireCliResource.toAspireResourceData(appHostPath: AspireAppHostPath): AspireResourceData {
    val resourceName = requireNotNull(name) { "Resource name is required to create a resource update" }
    val resourceState = state?.let(::mapResourceState)
    val resourceStateStyle = stateStyle?.let(::mapResourceStateStyle)

    var launchConfigurationType = ResourceLaunchConfigurationType.Unknown
    var parentName: String? = null
    var pid: AspireResourceProperty<Int>? = null
    var projectPath: AspireResourceProperty<AspirePath>? = null
    var executablePath: AspireResourceProperty<AspirePath>? = null
    var executableWorkDir: AspireResourceProperty<AspirePath>? = null
    var args: AspireResourceProperty<String>? = null
    var containerImage: AspireResourceProperty<String>? = null
    var containerId: AspireResourceProperty<String>? = null
    var containerPorts: AspireResourceProperty<String>? = null
    var containerCommand: AspireResourceProperty<String>? = null
    var containerArgs: AspireResourceProperty<String>? = null
    var containerLifetime: AspireResourceProperty<String>? = null
    var connectionString: AspireResourceProperty<String>? = null
    var value: AspireResourceProperty<String>? = null

    for ((propertyName, propertyValue) in properties.orEmpty()) {
        val propValue = propertyValue?.stringValue() ?: continue

        when (propertyName) {
            "resource.launchConfigurationType" -> launchConfigurationType = mapLaunchConfigurationType(propValue)
            "resource.parentName" -> parentName = propValue
            "executable.pid" -> pid = propValue.toIntOrNull()?.let { AspireResourceProperty(it, false) }
            "project.path" -> projectPath = AspireResourceProperty(AspirePath(propValue), false)
            "executable.path" -> executablePath = AspireResourceProperty(AspirePath(propValue), false)
            "executable.workDir" -> executableWorkDir = AspireResourceProperty(AspirePath(propValue), false)
            "executable.args" -> args = AspireResourceProperty(propValue, false)
            "container.image" -> containerImage = AspireResourceProperty(propValue, false)
            "container.id" -> containerId = AspireResourceProperty(propValue, false)
            "container.ports" -> containerPorts = AspireResourceProperty(propValue, false)
            "container.command" -> containerCommand = AspireResourceProperty(propValue, false)
            "container.args" -> containerArgs = AspireResourceProperty(propValue, false)
            "container.lifetime" -> containerLifetime = AspireResourceProperty(propValue, false)
            "resource.connectionString" -> connectionString = AspireResourceProperty(propValue, false)
            "Value" -> value = AspireResourceProperty(propValue, false)
        }
    }

    val resourceUrls = urls.orEmpty().mapNotNull {
        val url = it.url ?: return@mapNotNull null
        ResourceUrl(
            endpointName = it.name,
            fullUrl = url,
            isInternal = it.isInternal,
            isInactive = false,
            sortOrder = 0,
            displayName = it.displayName ?: it.name.orEmpty(),
        )
    }
    val resourceEnvironment = environment.orEmpty().map { (key, value) ->
        ResourceEnvironmentVariable(key, value)
    }
    val resourceVolumes = volumes.orEmpty().map {
        ResourceVolume(it.source.orEmpty(), it.target.orEmpty(), it.mountType.orEmpty(), it.isReadOnly)
    }
    val resourceRelationships = relationships.orEmpty().mapNotNull {
        val targetName = it.resourceName ?: return@mapNotNull null
        ResourceRelationship(targetName, it.type.orEmpty())
    }
    val resourceCommands = commands
        .orEmpty()
        .entries
        .sortedBy { it.value.sortOrder ?: 0 }
        .map { (commandName, command) ->
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
        }
    val timezone = TimeZone.currentSystemDefault()

    return AspireResourceData(
        id = AspireResourceId(appHostPath, resourceName),
        uid = uid ?: resourceName,
        name = resourceName,
        type = mapResourceType(resourceType.orEmpty()),
        launchConfigurationType = launchConfigurationType,
        originType = resourceType.orEmpty(),
        displayName = displayName ?: resourceName,
        state = resourceState,
        stateStyle = resourceStateStyle,
        isHidden = isHiddenResource(resourceName),
        healthStatus = calculateHealthStatus(resourceState),
        urls = resourceUrls,
        environment = resourceEnvironment,
        volumes = resourceVolumes,
        relationships = resourceRelationships,
        parentName = parentName,
        commands = resourceCommands,
        createdAt = creationTimestamp.toResourceTimestamp(timezone),
        startedAt = startTimestamp.toResourceTimestamp(timezone),
        stoppedAt = stopTimestamp.toResourceTimestamp(timezone),
        exitCode = exitCode?.let { AspireResourceProperty(it, false) },
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
        source = source?.let { AspireResourceProperty(it, false) },
        value = value,
    )
}

private fun isHiddenResource(resourceName: String): Boolean {
    // The CLI does not expose the actual isHidden field, so only resources known to be hidden are inferred from their names.
    // Switch to the actual isHidden field when the CLI exposes it.
    return resourceName.startsWith("aspire-dashboard-") || resourceName.contains("-rebuilder-")
}

private fun String?.toResourceTimestamp(timezone: TimeZone): LocalDateTime? {
    if (this == null) return null
    return try {
        Instant.parse(this).toLocalDateTime(timezone)
    } catch (_: IllegalArgumentException) {
        null
    }
}

private fun JsonElement.stringValue(): String? = when (this) {
    is JsonPrimitive -> contentOrNull
    is JsonArray -> joinToString(" ") { it.stringValue().orEmpty() }
    else -> toString()
}

private fun AspireCliResource.calculateHealthStatus(state: ResourceState?): ResourceHealthStatus? {
    if (state != ResourceState.Running) return null

    ResourceHealthStatus.entries.find { it.name == healthStatus }?.let { return it }

    if (healthReports.isNullOrEmpty()) return ResourceHealthStatus.Healthy

    val statuses = healthReports.values.map { it.status }
    if (statuses.any { it == null }) return ResourceHealthStatus.Unhealthy

    return when {
        statuses.any { it == "Unhealthy" } -> ResourceHealthStatus.Unhealthy
        statuses.any { it == "Degraded" } -> ResourceHealthStatus.Degraded
        else -> ResourceHealthStatus.Healthy
    }
}
