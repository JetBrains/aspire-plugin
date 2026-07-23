package com.jetbrains.aspire.worker.dcp

import com.intellij.testFramework.common.timeoutRunBlocking
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.WebSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

const val TEST_TOKEN: String = "test-token"
const val DEFAULT_API_VERSION: String = "2024-04-23"
const val DEFAULT_INSTANCE_ID: String = "abcdefGHIJK"
const val DEFAULT_HOST_ID: String = "abcde"
const val VALID_SESSION_BODY: String =
    """{"launch_configurations":[{"type":"project","project_path":"/p/App.csproj","mode":"Debug"}]}"""

private const val DCP_INSTANCE_ID_HEADER = "Microsoft-Developer-DCP-Instance-ID"

private val httpClient: HttpClient = HttpClient.newHttpClient()

/**
 * Starts an [AspireSessionServer] on an ephemeral loopback port, invokes [block] with its base URL,
 * then always stops it. Runs under [timeoutRunBlocking] so a stuck socket fails the test instead of
 * hanging the build.
 */
internal fun withServer(
    host: AspireSessionHost,
    token: String = TEST_TOKEN,
    block: suspend (baseUrl: String, server: AspireSessionServer) -> Unit,
) = timeoutRunBlocking {
    val server = AspireSessionServer(host, AspireSessionServerConfig(port = 0, token = token))
    server.start()
    try {
        block("http://127.0.0.1:${server.resolvedPort}", server)
    } finally {
        server.stop()
    }
}

internal fun httpGet(url: String): HttpResponse<String> {
    val request = HttpRequest.newBuilder(URI.create(url)).GET().build()
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
}

internal fun httpPut(
    url: String,
    body: String,
    token: String? = TEST_TOKEN,
    apiVersion: String? = DEFAULT_API_VERSION,
    instanceId: String? = DEFAULT_INSTANCE_ID,
): HttpResponse<String> {
    val request = HttpRequest.newBuilder(uriWithVersion(url, apiVersion))
        .PUT(HttpRequest.BodyPublishers.ofString(body))
        .header("Content-Type", "application/json")
        .auth(token)
        .instance(instanceId)
        .build()
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
}

internal fun httpDelete(
    url: String,
    token: String? = TEST_TOKEN,
    apiVersion: String? = DEFAULT_API_VERSION,
    instanceId: String? = DEFAULT_INSTANCE_ID,
): HttpResponse<String> {
    val request = HttpRequest.newBuilder(uriWithVersion(url, apiVersion))
        .DELETE()
        .auth(token)
        .instance(instanceId)
        .build()
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
}

private fun uriWithVersion(url: String, apiVersion: String?): URI =
    URI.create(if (apiVersion != null) "$url?api-version=$apiVersion" else url)

private fun HttpRequest.Builder.auth(token: String?): HttpRequest.Builder =
    apply { if (token != null) header("Authorization", "Bearer $token") }

private fun HttpRequest.Builder.instance(instanceId: String?): HttpRequest.Builder =
    apply { if (instanceId != null) header(DCP_INSTANCE_ID_HEADER, instanceId) }

internal fun HttpResponse<String>.assertStatus(expected: Int) =
    assertEquals(expected, statusCode(), "Unexpected status code; body=${body()}")

internal fun HttpResponse<String>.assertBodyContains(substring: String) =
    assertTrue(body().contains(substring), "Body did not contain '$substring': ${body()}")

internal fun HttpResponse<String>.location(): String? = headers().firstValue("Location").orElse(null)

internal fun connectNotify(
    baseUrl: String,
    listener: WebSocket.Listener,
    token: String? = TEST_TOKEN,
    apiVersion: String = DEFAULT_API_VERSION,
    instanceId: String? = DEFAULT_INSTANCE_ID,
): WebSocket {
    val wsUrl = baseUrl.replaceFirst("http", "ws") + "/run_session/notify?api-version=$apiVersion"
    val builder = httpClient.newWebSocketBuilder()
    if (token != null) builder.header("Authorization", "Bearer $token")
    if (instanceId != null) builder.header(DCP_INSTANCE_ID_HEADER, instanceId)
    return builder.buildAsync(URI.create(wsUrl), listener).get(5, TimeUnit.SECONDS)
}

internal class TestWsListener : WebSocket.Listener {
    private val messages = LinkedBlockingQueue<String>()
    private val closeCodes = LinkedBlockingQueue<Int>()
    private val buffer = StringBuilder()

    override fun onOpen(webSocket: WebSocket) {
        webSocket.request(1)
    }

    override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*> {
        buffer.append(data)
        if (last) {
            messages.add(buffer.toString())
            buffer.setLength(0)
        }
        webSocket.request(1)
        return CompletableFuture.completedFuture(null)
    }

    override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*> {
        closeCodes.add(statusCode)
        return CompletableFuture.completedFuture(null)
    }

    fun nextFrame(timeoutSeconds: Long = 5): String =
        checkNotNull(messages.poll(timeoutSeconds, TimeUnit.SECONDS)) { "Timed out waiting for a notify frame" }

    fun hasFrame(timeoutMillis: Long = 250): Boolean = messages.poll(timeoutMillis, TimeUnit.MILLISECONDS) != null

    fun nextClose(timeoutSeconds: Long = 5): Int =
        checkNotNull(closeCodes.poll(timeoutSeconds, TimeUnit.SECONDS)) { "Timed out waiting for notify close" }
}
