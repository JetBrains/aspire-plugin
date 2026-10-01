package com.jetbrains.aspire.sessions

import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import kotlinx.coroutines.channels.Channel
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
interface SessionRequest

@ApiStatus.Internal
data class StartSessionRequest(
    val sessionId: String,
    val launchConfiguration: SessionLaunchConfiguration,
    val sessionEvents: Channel<SessionEvent>,
    val aspireHostRunConfigName: String?,
    val sessionLifetime: LifetimeDefinition
) : SessionRequest

@ApiStatus.Internal
data class StopSessionRequest(
    val sessionId: String
) : SessionRequest
