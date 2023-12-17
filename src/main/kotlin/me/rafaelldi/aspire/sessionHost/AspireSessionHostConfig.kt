package me.rafaelldi.aspire.sessionHost

data class AspireSessionHostConfig(
    val id: String,
    val projectName: String,
    val isDebug: Boolean,
    val aspNetPort: Int
)