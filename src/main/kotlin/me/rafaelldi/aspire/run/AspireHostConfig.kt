package me.rafaelldi.aspire.run

import com.jetbrains.rd.util.lifetime.Lifetime
import java.nio.file.Path

data class AspireHostConfig(
    val name: String,
    val debugSessionToken: String,
    val debugSessionPort: Int,
    val aspireHostProjectPath: Path,
    val aspireHostProjectUrl: String,
    val debuggingMode: Boolean,
    val resourceServiceEndpointUrl: String?,
    val resourceServiceApiKey: String?,
    val aspireHostLifetime: Lifetime
)