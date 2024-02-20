package me.rafaelldi.aspire.services

import com.jetbrains.rd.util.lifetime.Lifetime
import me.rafaelldi.aspire.generated.AspireSessionHostModel
import java.nio.file.Path

data class AspireHostServiceData(
    val id: String,
    val name: String,
    val hostProjectPath: Path?,
    val dashboardUrl: String?,
    val sessionHostModel: AspireSessionHostModel,
    val sessionHostLifetime: Lifetime
)