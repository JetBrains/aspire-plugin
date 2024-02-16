package me.rafaelldi.aspire.sessionHost

import java.nio.file.Path

data class AspireSessionHostConfig(
    val debugSessionToken: String,
    val debugSessionPort: Int,
    val runProfileName: String,
    val aspireHostProjectPath: Path?,
    val aspireHostProjectUrl: String?,
    val isDebug: Boolean,
    val resourceServiceUrl: String?,
    val openTelemetryProtocolUrl: String?,
    val openTelemetryProtocolServerPort: Int
)