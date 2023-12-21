package me.rafaelldi.aspire.sessionHost

data class AspireSessionHostConfig(
    val id: String,
    val hostName: String,
    val isDebug: Boolean,
    val debugSessionPort: Int,
    val openTelemetryPort: Int,
    val dashboardUrl: String?,
    val openTelemetryProtocolUrl: String?
)