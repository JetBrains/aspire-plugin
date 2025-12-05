package com.jetbrains.aspire.sessions

import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import kotlinx.coroutines.channels.Channel
import java.nio.file.Path

interface SessionRequest

data class StartSessionRequest(
    val sessionId: String,
    val launchConfiguration: SessionLaunchConfiguration,
    val sessionEvents: Channel<SessionEvent>,
    val aspireHostRunConfigName: String?,
    val sessionLifetime: LifetimeDefinition
) : SessionRequest

data class StopSessionRequest(
    val sessionId: String
) : SessionRequest

sealed interface SessionLaunchConfiguration

data class DotNetSessionLaunchConfiguration(
    val projectPath: Path,
    val debug: Boolean,
    val launchProfile: String?,
    val disableLaunchProfile: Boolean,
    val args: List<String>?,
    val envs: List<Pair<String, String>>?
) : SessionLaunchConfiguration