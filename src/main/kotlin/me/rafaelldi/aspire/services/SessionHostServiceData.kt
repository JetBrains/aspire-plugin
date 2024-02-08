package me.rafaelldi.aspire.services

import com.jetbrains.rd.util.lifetime.Lifetime
import me.rafaelldi.aspire.generated.AspireSessionHostModel
import java.nio.file.Path

data class SessionHostServiceData(
    val id: String,
    val hostName: String,
    val hostPath: Path?,
    val dashboardUrl: String?,
    val sessionHostModel: AspireSessionHostModel,
    val sessionHostLifetime: Lifetime
)