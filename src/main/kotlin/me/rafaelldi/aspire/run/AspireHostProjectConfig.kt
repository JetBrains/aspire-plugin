package me.rafaelldi.aspire.run

import java.nio.file.Path

data class AspireHostProjectConfig(
    val debugSessionToken: String,
    val debugSessionPort: Int,
    val aspireHostProjectPath: Path,
    val aspireHostProjectUrl: String,
    val debuggingMode: Boolean,
    val resourceServiceEndpointUrl: String?,
    val resourceServiceApiKey: String?,
    val openTelemetryProtocolEndpointUrl: String?,
    val openTelemetryProtocolServerPort: Int
)