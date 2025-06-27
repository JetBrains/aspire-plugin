package com.jetbrains.rider.aspire.otlp

import com.intellij.openapi.extensions.ExtensionPointName

interface OpenTelemetryProtocolServerExtension {
    companion object {
        val EP_NAME =
            ExtensionPointName<OpenTelemetryProtocolServerExtension>("com.jetbrains.rider.aspire.openTelemetryProtocolServerExtension")
    }

    val enabled: Boolean

    fun getOTLPServerEndpoint(): String?

    fun setOTLPServerEndpointForProxying(endpoint: String)

    fun removeOTLPServerEndpointForProxying(endpoint: String)
}