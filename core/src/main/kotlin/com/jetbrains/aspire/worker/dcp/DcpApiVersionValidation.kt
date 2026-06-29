package com.jetbrains.aspire.worker.dcp

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*

/** The query parameter carrying the DCP protocol version, e.g. `?api-version=2024-04-23`. */
private const val API_VERSION_PARAM = "api-version"

internal class DcpApiVersionValidationConfig {
    /** Protocol versions the server accepts; compared case-insensitively. */
    var supportedVersions: List<String> = emptyList()
}

/**
 * A route-scoped plugin that rejects requests whose `api-version` query parameter is missing or
 * unsupported, before the route handler runs.
 *
 * Installed under an [authenticate] block, it uses the [AuthenticationChecked] hook so the Bearer
 * token is validated first (a failed auth answers 401 and this hook then bails via [ApplicationCall.isHandled]).
 * On an unsupported version it answers 400; because the routing handler is skipped once the call is
 * handled, the underlying `put`/`delete`/`webSocket` handler never runs.
 */
internal val DcpApiVersionValidation = createRouteScopedPlugin(
    name = "DcpApiVersionValidation",
    createConfiguration = ::DcpApiVersionValidationConfig,
) {
    val supportedVersions = pluginConfig.supportedVersions

    on(AuthenticationChecked) { call ->
        if (call.isHandled) return@on

        val apiVersion = call.request.queryParameters[API_VERSION_PARAM]
        val isSupported = apiVersion != null && supportedVersions.any { it.equals(apiVersion, ignoreCase = true) }
        if (!isSupported) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(DcpErrors.ProtocolVersionIsNotSupported.detail))
        }
    }
}
