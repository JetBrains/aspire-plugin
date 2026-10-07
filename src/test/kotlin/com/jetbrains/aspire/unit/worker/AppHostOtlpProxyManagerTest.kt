package com.jetbrains.aspire.unit.worker

import com.intellij.testFramework.common.timeoutRunBlocking
import com.jetbrains.aspire.otlp.OpenTelemetryProtocolServerExtension
import com.jetbrains.aspire.worker.AppHostOtlpProxyManager
import com.jetbrains.aspire.worker.AspireAppHost.AppHostEnvironment
import com.jetbrains.aspire.worker.AspireAppHost.AspireAppHostState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

internal class AppHostOtlpProxyManagerTest {
    private lateinit var scope: CoroutineScope
    private lateinit var extension: RecordingOtlpExtension
    private lateinit var appHostState: MutableStateFlow<AspireAppHostState>

    @BeforeEach
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        extension = RecordingOtlpExtension()
        appHostState = MutableStateFlow(AspireAppHostState.Inactive)
        val manager = AppHostOtlpProxyManager(scope) { extension }
        manager.observeAppHostState(appHostState)
    }

    @AfterEach
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `endpoint of the current environment is registered when the host is started`() = timeoutRunBlocking {
        val environment = AppHostEnvironment(null, null, "http://localhost:4317")

        appHostState.value = AspireAppHostState.Started(environment)

        extension.awaitEvents(listOf("set http://localhost:4317"))
    }

    @Test
    fun `endpoint is unregistered when the host is stopped after start`() = timeoutRunBlocking {
        val environment = AppHostEnvironment(null, null, "http://localhost:4317")
        appHostState.value = AspireAppHostState.Started(environment)
        extension.awaitEvents(listOf("set http://localhost:4317"))

        appHostState.value = AspireAppHostState.Stopped

        extension.awaitEvents(listOf("set http://localhost:4317", "remove http://localhost:4317"))
    }

    @Test
    fun `starting again replaces the previously registered endpoint`() = timeoutRunBlocking {
        val firstEnvironment = AppHostEnvironment(null, null, "http://localhost:4317")
        appHostState.value = AspireAppHostState.Started(firstEnvironment)
        extension.awaitEvents(listOf("set http://localhost:4317"))
        val environment = AppHostEnvironment(null, null, "http://localhost:4318")

        appHostState.value = AspireAppHostState.Started(environment)

        val expected = listOf(
            "set http://localhost:4317",
            "remove http://localhost:4317",
            "set http://localhost:4318",
        )
        extension.awaitEvents(expected)
    }

    private class RecordingOtlpExtension : OpenTelemetryProtocolServerExtension {
        val events = MutableStateFlow<List<String>>(emptyList())

        override val enabled: Boolean = true

        override fun getOTLPServerEndpoint(): String? = null

        override fun setOTLPServerEndpointForProxying(endpoint: String) {
            events.update { it + "set $endpoint" }
        }

        override fun removeOTLPServerEndpointForProxying(endpoint: String) {
            events.update { it + "remove $endpoint" }
        }

        suspend fun awaitEvents(expected: List<String>) {
            withTimeout(10.seconds) { events.first { it.size >= expected.size } }
            assertEquals(expected, events.value)
        }
    }
}
