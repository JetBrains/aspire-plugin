package com.jetbrains.aspire.sessions

import com.jetbrains.aspire.generated.CreateSessionRequest
import com.jetbrains.rd.util.lifetime.Lifetime
import kotlinx.coroutines.channels.Channel

interface SessionRequest

data class StartSessionRequest(
    val sessionId: String,
    val createSessionRequest: CreateSessionRequest,
    val sessionEvents: Channel<SessionEvent>,
    val aspireHostRunConfigName: String?,
    val aspireHostLifetime: Lifetime
) : SessionRequest

data class StopSessionRequest(
    val sessionId: String
) : SessionRequest