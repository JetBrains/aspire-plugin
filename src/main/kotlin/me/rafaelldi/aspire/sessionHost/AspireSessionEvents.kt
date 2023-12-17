package me.rafaelldi.aspire.sessionHost

interface AspireSessionEvent
data class AspireSessionStarted(val id: String, val pid: Long) : AspireSessionEvent
data class AspireSessionTerminated(val id: String, val exitCode: Int) : AspireSessionEvent
data class AspireSessionLogReceived(val id: String, val isStdErr: Boolean, val message: String) : AspireSessionEvent