package com.jetbrains.rider.aspire.util

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProjectRunPropertiesOutput(
    @SerialName("Properties") val properties: RunProperties
)

@Serializable
data class RunProperties(
    @SerialName("TargetFramework") val targetFramework: String,
    @SerialName("RunCommand") val runCommand: String,
    @SerialName("RunArguments") val runArguments: String,
    @SerialName("RunWorkingDirectory") val runWorkingDirectory: String,
)