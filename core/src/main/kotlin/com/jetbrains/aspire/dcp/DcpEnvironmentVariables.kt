package com.jetbrains.aspire.dcp

import com.jetbrains.aspire.util.DEBUG_SESSION_INFO
import com.jetbrains.aspire.util.DEBUG_SESSION_PORT
import com.jetbrains.aspire.util.DEBUG_SESSION_RUN_MODE
import com.jetbrains.aspire.util.DEBUG_SESSION_RUN_MODE_DEBUG
import com.jetbrains.aspire.util.DEBUG_SESSION_RUN_MODE_NO_DEBUG
import com.jetbrains.aspire.util.DEBUG_SESSION_SERVER_CERTIFICATE
import com.jetbrains.aspire.util.DEBUG_SESSION_TOKEN
import org.jetbrains.annotations.ApiStatus

/**
 * Builds the DCP "IDE-execution" environment variables the AppHost process must receive,
 * including the endpoint connection details, server capabilities, and service run mode.
 *
 * @param debugExecutor whether services should run under the debugger
 *
 * @see <a href="https://github.com/dotnet/aspire/blob/main/docs/specs/IDE-execution.md#enabling-ide-execution">Enabling IDE execution</a>
 */
@ApiStatus.Internal
fun buildDcpEnvironmentVariables(
    endpoint: AspireSessionServerEndpoint,
    debugExecutor: Boolean,
): Map<String, String> = buildMap {
    put(DEBUG_SESSION_TOKEN, endpoint.token)
    put(DEBUG_SESSION_PORT, "localhost:${endpoint.port}")
    put(DEBUG_SESSION_INFO, DcpJson.encodeToString(AspireSessionServer.getInfo()))
    put(DEBUG_SESSION_RUN_MODE, if (debugExecutor) DEBUG_SESSION_RUN_MODE_DEBUG else DEBUG_SESSION_RUN_MODE_NO_DEBUG)
    if (endpoint.isHttps) endpoint.base64Cert?.let { put(DEBUG_SESSION_SERVER_CERTIFICATE, it) }
}
