package com.jetbrains.aspire.worker.dcp

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

/**
 * Shared JSON format for the DCP protocol.
 *
 * - snake_case property names (matches `JsonNamingPolicy.SnakeCaseLower`)
 * - omit nulls and defaults
 * - `"type"` discriminator for the polymorphic [LaunchConfiguration] hierarchy
 * - tolerate unknown keys so newer DCP revisions don't break parsing
 */
@OptIn(ExperimentalSerializationApi::class)
internal val DcpJson: Json = Json {
    namingStrategy = JsonNamingStrategy.SnakeCase
    encodeDefaults = false
    explicitNulls = false
    classDiscriminator = "type"
    ignoreUnknownKeys = true
}

@Serializable
internal data class Info(
    val protocolsSupported: List<String>,
    val supportedLaunchConfigurations: List<String>? = null,
)

@Serializable
internal data class Session(
    val launchConfigurations: List<LaunchConfiguration>,
    val env: List<EnvironmentVariable>? = null,
    val args: List<String>? = null,
)

@Serializable
internal sealed class LaunchConfiguration

@Serializable
@SerialName("project")
internal data class ProjectLaunchConfiguration(
    val projectPath: String,
    val mode: Mode? = null,
    val launchProfile: String? = null,
    val disableLaunchProfile: Boolean? = null,
) : LaunchConfiguration()

@Serializable
internal enum class Mode {
    @SerialName("Debug")
    Debug,

    @Suppress("unused")
    @SerialName("NoDebug")
    NoDebug,
}

@Serializable
internal data class EnvironmentVariable(
    val name: String,
    val value: String? = null,
)

@Serializable
internal data class ErrorResponse(
    val error: ErrorDetail,
)

@Serializable
internal data class ErrorDetail(
    val code: String,
    val message: String,
    val details: List<ErrorDetail>? = null,
)

@Serializable
internal data class ProcessStartedEvent(
    val sessionId: String,
    val notificationType: String,
    val pid: Long,
)

@Serializable
internal data class ProcessTerminatedEvent(
    val sessionId: String,
    val notificationType: String,
    val exitCode: Int,
)

@Serializable
internal data class LogReceivedEvent(
    val sessionId: String,
    val notificationType: String,
    val isStdErr: Boolean,
    val logMessage: String,
)

@Serializable
internal data class MessageReceivedEvent(
    val sessionId: String,
    val notificationType: String,
    val level: String,
    val message: String,
    val code: String? = null,
    val details: List<ErrorDetail>? = null,
)
