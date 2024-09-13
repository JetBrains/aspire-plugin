package com.jetbrains.rider.aspire.sessionHost

interface SessionEvent
data class SessionStarted(val id: String, val pid: Long) : SessionEvent
data class SessionTerminated(val id: String, val exitCode: Int) : SessionEvent
data class SessionLogReceived(val id: String, val isStdErr: Boolean, val message: String) : SessionEvent