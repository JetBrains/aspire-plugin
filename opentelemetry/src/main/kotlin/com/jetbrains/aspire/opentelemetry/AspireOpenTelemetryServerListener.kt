package com.jetbrains.aspire.opentelemetry

import com.jetbrains.opentelemetry.core.service.OpenTelemetryServerListener

internal class AspireOpenTelemetryServerListener : OpenTelemetryServerListener {
    override fun serverStarted(otlpEndpoint: String, apiKey: String) {
    }

    override fun serverStopped() {
    }
}