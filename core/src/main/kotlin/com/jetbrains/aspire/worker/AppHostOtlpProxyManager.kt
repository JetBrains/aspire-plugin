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
 * - the endpoint is registered when the AppHost enters the [Starting][AspireAppHostState.Starting] state;
 * - it is unregistered when the AppHost stops after having started.
 */
@ApiStatus.Internal
class AppHostOtlpProxyManager(
    private val cs: CoroutineScope,
    private val extensionProvider: () -> OpenTelemetryProtocolServerExtension? =
        { OpenTelemetryProtocolServerExtension.getEnabledExtension() },
) {
    fun observeAppHostState(appHostState: StateFlow<AspireAppHostState>) {
        cs.launchOnAppHostTransitions(
            appHostState,
            onStarting = { starting ->
                val endpoint = starting.environment.otlpEndpointUrl
                val extension = extensionProvider()
                if (endpoint != null && extension != null) {
                    extension.setOTLPServerEndpointForProxying(endpoint)
                }
            },
            onStoppedAfterStart = { previous ->
                val endpoint = previous.environment.otlpEndpointUrl
                val extension = extensionProvider()
                if (endpoint != null && extension != null) {
                    extension.removeOTLPServerEndpointForProxying(endpoint)
                }
            },
        )
    }
}
