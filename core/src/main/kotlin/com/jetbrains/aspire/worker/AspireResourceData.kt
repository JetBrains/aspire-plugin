package com.jetbrains.aspire.worker

import com.jetbrains.aspire.generated.*
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.math.roundToInt

@ApiStatus.Internal
data class AspireResourceProperty<T>(val value: T, val isSensitive: Boolean)

@ApiStatus.Internal
data class AspireResourceData(
    val uid: String,
    val name: String,
    val type: ResourceType,
    val displayName: String,
    val state: ResourceState?,
    val isHidden: Boolean,
    val healthStatus: ResourceHealthStatus?,
    val urls: List<ResourceUrl>,
    val environment: List<ResourceEnvironmentVariable>,
    val volumes: List<ResourceVolume>,
    val relationships: List<ResourceRelationship>,
    val commands: List<ResourceCommand>,
    val createdAt: LocalDateTime?,
    val startedAt: LocalDateTime?,
    val stoppedAt: LocalDateTime?,
    val exitCode: AspireResourceProperty<Int>?,
    val pid: AspireResourceProperty<Int>?,
    val projectPath: AspireResourceProperty<Path>?,
    val executablePath: AspireResourceProperty<Path>?,
    val executableWorkDir: AspireResourceProperty<Path>?,
    val args: AspireResourceProperty<String>?,
    val containerImage: AspireResourceProperty<String>?,
    val containerId: AspireResourceProperty<String>?,
    val containerPorts: AspireResourceProperty<String>?,
    val containerCommand: AspireResourceProperty<String>?,
    val containerArgs: AspireResourceProperty<String>?,
    val containerLifetime: AspireResourceProperty<String>?,
    val connectionString: AspireResourceProperty<String>?,
    val source: AspireResourceProperty<String>?,
    val value: AspireResourceProperty<String>?
) {
    val parentResourceName: String?
        get() = relationships.firstOrNull { it.type.equals("parent", true) }?.resourceName
}

internal fun ResourceModel?.toAspireResourceData(previousState: ResourceState? = null): AspireResourceData {
    val timezone = TimeZone.currentSystemDefault()

    val createdAt = this?.createdAt
        ?.time
        ?.let { Instant.fromEpochMilliseconds(it) }
        ?.toLocalDateTime(timezone)

    val startedAt = this?.startedAt
        ?.time
        ?.let { Instant.fromEpochMilliseconds(it) }
        ?.toLocalDateTime(timezone)

    val stoppedAt = this?.stoppedAt
        ?.time
        ?.let { Instant.fromEpochMilliseconds(it) }
        ?.toLocalDateTime(timezone)

    val healthStatus = extractHealthStatus(this?.healthReports ?: emptyArray())

    val state = if (previousState != ResourceState.Hidden) this?.state else ResourceState.Hidden

    val properties = this?.properties ?: emptyArray()

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

    for (property in properties) {
        val propValue = property.value ?: continue
        val isSensitive = property.isSensitive == true

        when (property.name) {
            "resource.exitCode" -> exitCode = AspireResourceProperty(propValue.toDouble().roundToInt(), isSensitive)
            "executable.pid" -> pid = AspireResourceProperty(propValue.toInt(), isSensitive)
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

    return AspireResourceData(
        uid = this?.uid ?: "",
        name = this?.name ?: "",
        type = this?.type ?: ResourceType.Unknown,
        displayName = this?.displayName ?: "",
        state = state,
        isHidden = this?.isHidden ?: false,
        healthStatus = healthStatus,
        urls = this?.urls?.toList() ?: emptyList(),
        environment = this?.environment?.toList() ?: emptyList(),
        volumes = this?.volumes?.toList() ?: emptyList(),
        relationships = this?.relationships?.toList() ?: emptyList(),
        commands = this?.commands?.toList() ?: emptyList(),
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

private fun extractHealthStatus(healthReports: Array<ResourceHealthReport>): ResourceHealthStatus? {
    if (healthReports.isEmpty()) return null
    return healthReports.last().status
}


