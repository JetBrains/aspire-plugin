package com.jetbrains.aspire.sessions

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
enum class MessageLevel {
    Error,
    Info,
    Debug,
}

sealed interface SessionEvent
data class SessionProcessStarted(val id: String, val pid: Long) : SessionEvent
data class SessionProcessTerminated(val id: String, val exitCode: Int) : SessionEvent
data class SessionLogReceived(val id: String, val isStdErr: Boolean, val message: String) : SessionEvent
data class SessionMessageReceived(
    val id: String,
    val level: MessageLevel,
    val message: String,
    val errorCode: ErrorCode?,
) : SessionEvent

@ApiStatus.Internal
abstract class CreateSessionRequest {
    abstract val dcpInstancePrefix: String
    abstract val debug: Boolean
    abstract val args: List<String>?
    abstract val envs: List<SessionEnvironmentVariable>?
}

@ApiStatus.Internal
data class CreateProjectSessionRequest(
    val projectPath: String,
    val launchProfile: String?,
    val disableLaunchProfile: Boolean,
    override val dcpInstancePrefix: String,
    override val debug: Boolean,
    override val args: List<String>?,
    override val envs: List<SessionEnvironmentVariable>?,
) : CreateSessionRequest()

@ApiStatus.Internal
internal data class CreatePythonSessionRequest(
    val programPath: String,
    val interpreterPath: String?,
    val module: String?,
    override val dcpInstancePrefix: String,
    override val debug: Boolean,
    override val args: List<String>?,
    override val envs: List<SessionEnvironmentVariable>?,
) : CreateSessionRequest()

@ApiStatus.Internal
data class SessionEnvironmentVariable(val key: String, val value: String)

@ApiStatus.Internal
data class CreateSessionResponse(val sessionId: String?, val error: ErrorCode?)

@ApiStatus.Internal
data class DeleteSessionRequest(val dcpInstancePrefix: String, val sessionId: String)

@ApiStatus.Internal
data class DeleteSessionResponse(val sessionId: String?, val error: ErrorCode?)

@ApiStatus.Internal
enum class ErrorCode {
    AspireAppHostNotFound,
    UnsupportedLaunchConfigurationType,
    AspireSessionNotFound,
    DotNetProjectNotFound,
    UnableToFindSupportedLaunchConfiguration,
    Unexpected,
}
