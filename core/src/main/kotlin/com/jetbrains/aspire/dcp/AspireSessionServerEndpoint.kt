package com.jetbrains.aspire.dcp

import org.jetbrains.annotations.ApiStatus

/**
 * The bound endpoint of a started [AspireSessionServer]: the values a DCP client needs to connect back.
 *
 * @param port the loopback port the server is listening on
 * @param token the Bearer token DCP must present on the `/run_session` endpoints
 * @param isHttps whether the server terminates TLS (`https`) or serves plain `http`
 * @param base64Cert the base64-encoded server certificate DCP should trust for HTTPS
 */
@ApiStatus.Internal
data class AspireSessionServerEndpoint(
    val port: Int,
    val token: String,
    val isHttps: Boolean,
    val base64Cert: String?,
)
