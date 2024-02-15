package me.rafaelldi.aspire.sessionHost

import java.nio.file.Path

data class AspireSessionHostConfig(
    val id: String,
    val hostName: String,
    val hostPath: Path?,
    val isDebug: Boolean,
    val debugSessionPort: Int,
    val openTelemetryPort: Int,
    val dashboardUrl: String?,
    val resourceServiceUrl: String?,
    val openTelemetryProtocolUrl: String?
)