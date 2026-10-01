package com.jetbrains.aspire.sessions

import kotlinx.coroutines.channels.ReceiveChannel
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
interface AspireSessionHost {
    val sessionEvents: ReceiveChannel<SessionEvent>

    fun createSession(createSessionRequest: CreateSessionRequest): CreateSessionResponse

    fun deleteSession(deleteSessionRequest: DeleteSessionRequest): DeleteSessionResponse
}

@ApiStatus.Internal
enum class MessageLevel {
    Error,
    Info,
    Debug,
}

@ApiStatus.Internal
enum class ErrorCode {
    AspireAppHostNotFound,
    UnsupportedLaunchConfigurationType,
    AspireSessionNotFound,
    DotNetProjectNotFound,
    UnableToFindSupportedLaunchConfiguration,
    Unexpected,
}

@ApiStatus.Internal
sealed interface SessionEvent

@ApiStatus.Internal
data class SessionProcessStarted(
    val id: String,
    val pid: Long
) : SessionEvent

@ApiStatus.Internal
data class SessionProcessTerminated(
    val id: String,
    val exitCode: Int
) : SessionEvent

@ApiStatus.Internal
data class SessionLogReceived(
    val id: String,
    val isStdErr: Boolean,
    val message: String
) : SessionEvent

@ApiStatus.Internal
data class SessionMessageReceived(
    val id: String,
    val level: MessageLevel,
    val message: String,
    val errorCode: ErrorCode?,
) : SessionEvent

@ApiStatus.Internal
data class CreateSessionRequest(
    val dcpInstancePrefix: String,
    val launchConfiguration: SessionLaunchConfiguration,
)

@ApiStatus.Internal
data class CreateSessionResponse(
    val sessionId: String?,
    val error: ErrorCode?
)

@ApiStatus.Internal
data class DeleteSessionRequest(
    val dcpInstancePrefix: String,
    val sessionId: String
)

@ApiStatus.Internal
data class DeleteSessionResponse(
    val sessionId: String?,
    val error: ErrorCode?
)

@ApiStatus.Internal
sealed interface SessionLaunchConfiguration

@ApiStatus.Internal
data class DotNetSessionLaunchConfiguration(
    val projectPath: Path,
    val debug: Boolean,
    val launchProfile: String?,
    val disableLaunchProfile: Boolean,
    val args: List<String>?,
    val envs: List<Pair<String, String>>?
) : SessionLaunchConfiguration