package com.jetbrains.aspire.rider.processLaunchers

import java.nio.file.Path

internal data class SessionProcessLaunchConfiguration(
    val projectPath: Path,
    val launchProfile: String?,
    val disableLaunchProfile: Boolean,
    val args: List<String>?,
    val envs: List<Pair<String, String>>?,
    val externalBuild: ExternalBuildSettings? = null,
)

/**
 * Inputs of the external (AppHost-coordinated) build that produced the project output.
 */
internal data class ExternalBuildSettings(
    val configuration: String?,
    val environment: Map<String, String>,
    val workingDirectory: Path?,
)
