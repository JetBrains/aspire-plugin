package com.jetbrains.aspire.resources

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class AspireResourceCommandRequest(
    val resourceName: String,
    val resourceType: String,
    val commandName: String,
)

@ApiStatus.Internal
data class AspireResourceCommandResponse(
    val kind: AspireResourceCommandResponseKind = AspireResourceCommandResponseKind.Undefined,
    val message: String? = null,
    val result: AspireResourceCommandResult? = null,
)

@ApiStatus.Internal
enum class AspireResourceCommandResponseKind {
    Undefined,
    Succeeded,
    Failed,
    Cancelled,
    InvalidArguments,
}

@ApiStatus.Internal
data class AspireResourceCommandResult(
    val value: String,
    val format: AspireResourceCommandResultFormat,
    val displayImmediately: Boolean,
)

@ApiStatus.Internal
enum class AspireResourceCommandResultFormat {
    None,
    Text,
    Json,
    Markdown,
}
