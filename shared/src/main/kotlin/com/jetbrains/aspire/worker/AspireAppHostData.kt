package com.jetbrains.aspire.worker

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
data class AspireAppHostData(
    val id: AspireAppHostId,
    val name: String,
    val status: AspireAppHostStatus,
    val dashboardUrl: String?,
)

@ApiStatus.Internal
@Serializable
enum class AspireAppHostStatus {
    Inactive,
    Starting,
    Started,
    Stopped,
}
