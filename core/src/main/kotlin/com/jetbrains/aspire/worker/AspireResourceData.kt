package com.jetbrains.aspire.worker

import kotlinx.datetime.LocalDateTime
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
data class AspireResourceProperty<T>(val value: T, val isSensitive: Boolean)

@ApiStatus.Internal
data class AspireResourceData(
    val uid: String,
    val name: String,
    val type: ResourceType,
    val originType: String,
    val displayName: String,
    val state: ResourceState?,
    val stateStyle: ResourceStateStyle?,
    val isHidden: Boolean,
    val healthStatus: ResourceHealthStatus?,
    val urls: List<ResourceUrl>,
    val environment: List<ResourceEnvironmentVariable>,
    val volumes: List<ResourceVolume>,
    val relationships: List<ResourceRelationship>,
    val parentDisplayName: String?,
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
)
