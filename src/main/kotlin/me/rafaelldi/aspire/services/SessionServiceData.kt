package me.rafaelldi.aspire.services

import com.jetbrains.rd.util.lifetime.Lifetime
import me.rafaelldi.aspire.generated.SessionModel

data class SessionServiceData(
    val sessionModel: SessionModel,
    val sessionLifetime: Lifetime,
)