package com.jetbrains.aspire.worker.dcp

import com.jetbrains.aspire.util.DEBUG_SESSION_PORT
import com.jetbrains.aspire.util.DEBUG_SESSION_SERVER_CERTIFICATE
import com.jetbrains.aspire.util.DEBUG_SESSION_TOKEN
import org.jetbrains.annotations.ApiStatus

/**
 * The bound endpoint of a started [AspireSessionServer]: the values a DCP client needs to connect back.
 *
 * @param port the loopback port the server is listening on
 * @param token the Bearer token DCP must present on the `/run_session` endpoints
 * @param isHttps whether the server terminates TLS (`https`) or serves plain `http`
 */
@ApiStatus.Internal
data class AspireSessionServerEndpoint(
    val port: Int,
    val token: String,
    val isHttps: Boolean,
)

/**
 * Builds the DCP "IDE-execution" connection environment variables the AppHost process must receive to
 * connect back to this endpoint.
 *
 * @param base64Cert the base64-encoded server certificate DCP should trust; only emitted for an HTTPS endpoint
 * @see <a href="https://github.com/dotnet/aspire/blob/main/docs/specs/IDE-execution.md#enabling-ide-execution">Enabling IDE execution</a>
 */
@ApiStatus.Internal
fun AspireSessionServerEndpoint.toDcpEnvironmentVariables(base64Cert: String?): Map<String, String> = buildMap {
    put(DEBUG_SESSION_TOKEN, token)
    put(DEBUG_SESSION_PORT, "localhost:$port")
    if (isHttps) base64Cert?.let { put(DEBUG_SESSION_SERVER_CERTIFICATE, it) }
}
