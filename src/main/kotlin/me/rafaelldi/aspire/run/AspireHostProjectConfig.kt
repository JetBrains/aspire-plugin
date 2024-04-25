package me.rafaelldi.aspire.run

import java.nio.file.Path

data class AspireHostProjectConfig(
    val debugSessionToken: String,
    val debugSessionPort: Int,
    val aspireHostProjectPath: Path,
    val aspireHostProjectUrl: String,
    val debuggingMode: Boolean,
    val resourceServiceUrl: String?,
    val resourceServiceApiKey: String?,
    val openTelemetryProtocolUrl: String?,
    val openTelemetryProtocolServerPort: Int
)