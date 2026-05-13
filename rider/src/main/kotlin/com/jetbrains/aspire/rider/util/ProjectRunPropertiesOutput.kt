package com.jetbrains.aspire.rider.util

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ProjectRunPropertiesOutput(
    @SerialName("Properties") val properties: RunProperties
)

@Serializable
internal data class RunProperties(
    @SerialName("TargetFramework") val targetFramework: String,
    @SerialName("RunCommand") val runCommand: String,
    @SerialName("RunArguments") val runArguments: String,
    @SerialName("RunWorkingDirectory") val runWorkingDirectory: String,
)