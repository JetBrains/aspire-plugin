package me.rafaelldi.aspire.sessionHost

data class AspireHostConfig(
    val id: String,
    val projectName: String,
    val isDebug: Boolean,
    val aspNetPort: Int
)