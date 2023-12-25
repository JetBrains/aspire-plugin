package me.rafaelldi.aspire.services

import com.jetbrains.rd.util.lifetime.Lifetime
import me.rafaelldi.aspire.generated.AspireSessionHostModel

data class SessionHostServiceData(
    val id: String,
    val hostName: String,
    val dashboardUrl: String?,
    val sessionHostModel: AspireSessionHostModel,
    val sessionHostLifetime: Lifetime
)