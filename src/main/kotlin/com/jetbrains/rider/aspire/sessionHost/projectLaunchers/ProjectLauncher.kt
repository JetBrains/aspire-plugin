package com.jetbrains.rider.aspire.sessionHost.projectLaunchers

import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.aspire.generated.SessionModel
import com.jetbrains.rider.aspire.sessionHost.SessionEvent
import kotlinx.coroutines.flow.MutableSharedFlow

interface ProjectLauncher {
    suspend fun launchRunSession(
        sessionId: String,
        sessionModel: SessionModel,
        sessionLifetime: Lifetime,
        sessionEvents: MutableSharedFlow<SessionEvent>
    )

    suspend fun launchDebugSession(
        sessionId: String,
        sessionModel: SessionModel,
        sessionLifetime: Lifetime,
        sessionEvents: MutableSharedFlow<SessionEvent>
    )
}