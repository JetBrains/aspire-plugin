package com.jetbrains.aspire.rider.util

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@Serializable
internal data class ProjectRunPropertiesOutput(
    @SerialName("Properties") val properties: RunProperties
)

/**
 * The raw MSBuild run properties of a project.
 */
@ApiStatus.Internal
@Serializable
data class RunProperties(
    @SerialName("TargetFramework") val targetFramework: String,
    @SerialName("RunCommand") val runCommand: String,
    @SerialName("RunArguments") val runArguments: String,
    @SerialName("RunWorkingDirectory") val runWorkingDirectory: String,
    @SerialName("TargetPath") val targetPath: String = "",
)
