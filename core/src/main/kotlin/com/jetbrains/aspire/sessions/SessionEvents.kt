package com.jetbrains.aspire.sessions

import com.jetbrains.aspire.generated.MessageLevel

sealed interface SessionEvent
data class SessionProcessStarted(val id: String, val pid: Long) : SessionEvent
data class SessionProcessTerminated(val id: String, val exitCode: Int) : SessionEvent
data class SessionLogReceived(val id: String, val isStdErr: Boolean, val message: String) : SessionEvent
data class SessionMessageReceived(
    val id: String,
    val level: MessageLevel,
    val message: String,
    val code: String?,
    val details: String?
) : SessionEvent