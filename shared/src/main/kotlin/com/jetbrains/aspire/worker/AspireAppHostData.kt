package com.jetbrains.aspire.worker

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
data class AspireAppHostData(
    val path: AspireAppHostPath,
    val name: String,
    val status: AspireAppHostStatus,
)

@ApiStatus.Internal
@Serializable
enum class AspireAppHostStatus {
    Inactive,
    Started,
    Stopped,
}
