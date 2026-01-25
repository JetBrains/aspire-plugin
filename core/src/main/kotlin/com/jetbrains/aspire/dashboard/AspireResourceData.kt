package com.jetbrains.aspire.dashboard

import com.jetbrains.aspire.generated.*
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.math.roundToInt

data class AspireResourceProperty<T>(val value: T, val isSensitive: Boolean)

data class AspireResourceData(
    val uid: String,
    val name: String,
    val type: ResourceType,
    val displayName: String,
    val state: ResourceState?,
    val isHidden: Boolean,
    val healthStatus: ResourceHealthStatus?,
    val urls: Array<ResourceUrl>,
    val environment: Array<ResourceEnvironmentVariable>,
    val volumes: Array<ResourceVolume>,
    val relationships: Array<ResourceRelationship>,
    val commands: Array<ResourceCommand>,
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

    companion object {
        fun fromModel(model: ResourceModel?, previousState: ResourceState? = null): AspireResourceData {
            val timezone = TimeZone.currentSystemDefault()

            val createdAt = model?.createdAt
                ?.time
                ?.let { Instant.fromEpochMilliseconds(it) }
                ?.toLocalDateTime(timezone)

            val startedAt = model?.startedAt
                ?.time
                ?.let { Instant.fromEpochMilliseconds(it) }
                ?.toLocalDateTime(timezone)

            val stoppedAt = model?.stoppedAt
                ?.time
                ?.let { Instant.fromEpochMilliseconds(it) }
                ?.toLocalDateTime(timezone)

            val healthStatus = extractHealthStatus(model?.healthReports ?: emptyArray())
            val properties = extractProperties(model?.properties ?: emptyArray())

            val state = if (previousState != ResourceState.Hidden) model?.state else ResourceState.Hidden

            return AspireResourceData(
                uid = model?.uid ?: "",
                name = model?.name ?: "",
                type = model?.type ?: ResourceType.Unknown,
                displayName = model?.displayName ?: "",
                state = state,
                isHidden = model?.isHidden ?: false,
                healthStatus = healthStatus,
                urls = model?.urls ?: emptyArray(),
                environment = model?.environment ?: emptyArray(),
                volumes = model?.volumes ?: emptyArray(),
                relationships = model?.relationships ?: emptyArray(),
                commands = model?.commands ?: emptyArray(),
                createdAt = createdAt,
                startedAt = startedAt,
                stoppedAt = stoppedAt,
                exitCode = properties.exitCode,
                pid = properties.pid,
                projectPath = properties.projectPath,
                executablePath = properties.executablePath,
                executableWorkDir = properties.executableWorkDir,
                args = properties.args,
                containerImage = properties.containerImage,
                containerId = properties.containerId,
                containerPorts = properties.containerPorts,
                containerCommand = properties.containerCommand,
                containerArgs = properties.containerArgs,
                containerLifetime = properties.containerLifetime,
                connectionString = properties.connectionString,
                source = properties.source,
                value = properties.value
            )
        }

        private fun extractHealthStatus(healthReports: Array<ResourceHealthReport>): ResourceHealthStatus? {
            if (healthReports.isEmpty()) return null
            return healthReports.last().status
        }

        private fun extractProperties(properties: Array<ResourceProperty>): ExtractedProperties {
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
                when (property.name) {
                    "resource.exitCode" -> {
                        property.value?.let {
                            exitCode = AspireResourceProperty(it.toDouble().roundToInt(), property.isSensitive == true)
                        }
                    }

                    "project.path" -> {
                        property.value?.let {
                            projectPath = AspireResourceProperty(Path(it), property.isSensitive == true)
                        }
                    }

                    "executable.path" -> {
                        property.value?.let {
                            executablePath = AspireResourceProperty(Path(it), property.isSensitive == true)
                        }
                    }

                    "executable.pid" -> {
                        property.value?.let {
                            pid = AspireResourceProperty(it.toInt(), property.isSensitive == true)
                        }
                    }

                    "executable.workDir" -> {
                        property.value?.let {
                            executableWorkDir = AspireResourceProperty(Path(it), property.isSensitive == true)
                        }
                    }

                    "executable.args" -> {
                        property.value?.let {
                            args = AspireResourceProperty(it, property.isSensitive == true)
                        }
                    }

                    "container.id" -> {
                        property.value?.let {
                            containerId = AspireResourceProperty(it, property.isSensitive == true)
                        }
                    }

                    "container.image" -> {
                        property.value?.let {
                            containerImage = AspireResourceProperty(it, property.isSensitive == true)
                        }
                    }

                    "container.ports" -> {
                        property.value?.let {
                            containerPorts = AspireResourceProperty(it, property.isSensitive == true)
                        }
                    }

                    "container.command" -> {
                        property.value?.let {
                            containerCommand = AspireResourceProperty(it, property.isSensitive == true)
                        }
                    }

                    "container.args" -> {
                        property.value?.let {
                            containerArgs = AspireResourceProperty(it, property.isSensitive == true)
                        }
                    }

                    "container.lifetime" -> {
                        property.value?.let {
                            containerLifetime = AspireResourceProperty(it, property.isSensitive == true)
                        }
                    }

                    "resource.connectionString" -> {
                        property.value?.let {
                            connectionString = AspireResourceProperty(it, property.isSensitive == true)
                        }
                    }

                    "resource.source" -> {
                        property.value?.let {
                            source = AspireResourceProperty(it, property.isSensitive == true)
                        }
                    }

                    "Value" -> {
                        property.value?.let {
                            value = AspireResourceProperty(it, property.isSensitive == true)
                        }
                    }
                }
            }

            return ExtractedProperties(
                exitCode, pid, projectPath, executablePath, executableWorkDir,
                args, containerImage, containerId, containerPorts, containerCommand,
                containerArgs, containerLifetime, connectionString, source, value
            )
        }

        private data class ExtractedProperties(
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
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AspireResourceData

        if (uid != other.uid) return false
        if (name != other.name) return false
        if (type != other.type) return false
        if (displayName != other.displayName) return false
        if (state != other.state) return false
        if (isHidden != other.isHidden) return false
        if (healthStatus != other.healthStatus) return false
        if (!urls.contentEquals(other.urls)) return false
        if (!environment.contentEquals(other.environment)) return false
        if (!volumes.contentEquals(other.volumes)) return false
        if (!relationships.contentEquals(other.relationships)) return false
        if (!commands.contentEquals(other.commands)) return false
        if (createdAt != other.createdAt) return false
        if (startedAt != other.startedAt) return false
        if (stoppedAt != other.stoppedAt) return false
        if (exitCode != other.exitCode) return false
        if (pid != other.pid) return false
        if (projectPath != other.projectPath) return false
        if (executablePath != other.executablePath) return false
        if (executableWorkDir != other.executableWorkDir) return false
        if (args != other.args) return false
        if (containerImage != other.containerImage) return false
        if (containerId != other.containerId) return false
        if (containerPorts != other.containerPorts) return false
        if (containerCommand != other.containerCommand) return false
        if (containerArgs != other.containerArgs) return false
        if (containerLifetime != other.containerLifetime) return false
        if (connectionString != other.connectionString) return false
        if (source != other.source) return false
        if (value != other.value) return false

        return true
    }

    override fun hashCode(): Int {
        var result = uid.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + displayName.hashCode()
        result = 31 * result + (state?.hashCode() ?: 0)
        result = 31 * result + isHidden.hashCode()
        result = 31 * result + (healthStatus?.hashCode() ?: 0)
        result = 31 * result + urls.contentHashCode()
        result = 31 * result + environment.contentHashCode()
        result = 31 * result + volumes.contentHashCode()
        result = 31 * result + relationships.contentHashCode()
        result = 31 * result + commands.contentHashCode()
        result = 31 * result + (createdAt?.hashCode() ?: 0)
        result = 31 * result + (startedAt?.hashCode() ?: 0)
        result = 31 * result + (stoppedAt?.hashCode() ?: 0)
        result = 31 * result + (exitCode?.hashCode() ?: 0)
        result = 31 * result + (pid?.hashCode() ?: 0)
        result = 31 * result + (projectPath?.hashCode() ?: 0)
        result = 31 * result + (executablePath?.hashCode() ?: 0)
        result = 31 * result + (executableWorkDir?.hashCode() ?: 0)
        result = 31 * result + (args?.hashCode() ?: 0)
        result = 31 * result + (containerImage?.hashCode() ?: 0)
        result = 31 * result + (containerId?.hashCode() ?: 0)
        result = 31 * result + (containerPorts?.hashCode() ?: 0)
        result = 31 * result + (containerCommand?.hashCode() ?: 0)
        result = 31 * result + (containerArgs?.hashCode() ?: 0)
        result = 31 * result + (containerLifetime?.hashCode() ?: 0)
        result = 31 * result + (connectionString?.hashCode() ?: 0)
        result = 31 * result + (source?.hashCode() ?: 0)
        result = 31 * result + (value?.hashCode() ?: 0)
        return result
    }
}
