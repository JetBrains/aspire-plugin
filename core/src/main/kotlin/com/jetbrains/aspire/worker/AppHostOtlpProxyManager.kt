@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.worker

import com.jetbrains.aspire.otlp.OpenTelemetryProtocolServerExtension
import com.jetbrains.aspire.worker.AspireAppHost.AspireAppHostState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus

/**
 * Registers the AppHost's OTLP endpoint for proxying with the enabled
 * [OpenTelemetryProtocolServerExtension], driven by the AppHost lifecycle:
 *
 * - the endpoint of the current environment is registered when the AppHost enters
 *   the [Started][AspireAppHostState.Started] state, replacing a previously registered one;
 * - it is unregistered when the AppHost stops after having started.
 */
@ApiStatus.Internal
class AppHostOtlpProxyManager(
    private val cs: CoroutineScope,
    private val extensionProvider: () -> OpenTelemetryProtocolServerExtension? =
        { OpenTelemetryProtocolServerExtension.getEnabledExtension() },
) {
    fun observeAppHostState(
        appHostState: StateFlow<AspireAppHostState>,
    ) {
        var registeredEndpoint: String? = null

        cs.launchOnAppHostTransitions(
            appHostState,
            onStarted = { state ->
                registeredEndpoint?.let { unregister(it) }
                val endpoint = state.environment.otlpEndpointUrl
                val extension = extensionProvider()
                registeredEndpoint = if (endpoint != null && extension != null) {
                    extension.setOTLPServerEndpointForProxying(endpoint)
                    endpoint
                } else {
                    null
                }
            },
            onStoppedAfterStart = {
                registeredEndpoint?.let { unregister(it) }
                registeredEndpoint = null
            },
        )
    }

    private fun unregister(endpoint: String) {
        extensionProvider()?.removeOTLPServerEndpointForProxying(endpoint)
    }
}
