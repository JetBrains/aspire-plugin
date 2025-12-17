package com.jetbrains.aspire.otlp

import com.intellij.openapi.extensions.ExtensionPointName

interface OpenTelemetryProtocolServerExtension {
    companion object {
        private val EP_NAME =
            ExtensionPointName<OpenTelemetryProtocolServerExtension>("com.jetbrains.aspire.openTelemetryProtocolServerExtension")

        fun getEnabledExtension(): OpenTelemetryProtocolServerExtension? {
            return EP_NAME.extensionList.singleOrNull { it.enabled }
        }
    }

    val enabled: Boolean

    fun getOTLPServerEndpoint(): String?

    fun setOTLPServerEndpointForProxying(endpoint: String)

    fun removeOTLPServerEndpointForProxying(endpoint: String)
}