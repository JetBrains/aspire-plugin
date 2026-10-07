package com.jetbrains.aspire.worker

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
data class AspireResourceCommandRequest(
    val resourceName: String,
    val resourceType: String,
    val commandName: String,
)

@ApiStatus.Internal
@Serializable
data class AspireResourceCommandResponse(
    val kind: AspireResourceCommandResponseKind = AspireResourceCommandResponseKind.Undefined,
    val message: String? = null,
    val result: AspireResourceCommandResult? = null,
)

@ApiStatus.Internal
@Serializable
enum class AspireResourceCommandResponseKind {
    Undefined,
    Succeeded,
    Failed,
    Cancelled,
    InvalidArguments,
}

@ApiStatus.Internal
@Serializable
data class AspireResourceCommandResult(
    val value: String,
    val format: AspireResourceCommandResultFormat,
    val displayImmediately: Boolean,
)

@ApiStatus.Internal
@Serializable
enum class AspireResourceCommandResultFormat {
    None,
    Text,
    Json,
    Markdown,
}
