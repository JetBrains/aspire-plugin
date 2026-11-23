package com.jetbrains.aspire.rider.sessions

import java.nio.file.Path

internal data class SessionProcessLaunchConfiguration(
    val projectPath: Path,
    val launchProfile: String?,
    val disableLaunchProfile: Boolean,
    val args: List<String>?,
    val envs: List<Pair<String, String>>?
)
