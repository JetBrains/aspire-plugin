package com.jetbrains.rider.aspire.worker

/**
 * Configuration data class for an Aspire worker instance.
 *
 * @property rdPort The port used to connect to the Aspire worker via RD protocol.
 * @property debugSessionToken The token used to authenticate Aspire DCP requests.
 * @property debugSessionPort The port used for communication between Aspire worker and Aspire DCP.
 * @property useHttps Indicates whether HTTPS is used for communication between Aspire worker and Aspire DCP.
 */
internal data class AspireWorkerConfig(
    val rdPort: Int,
    val debugSessionToken: String,
    val debugSessionPort: Int,
    val useHttps: Boolean,
)