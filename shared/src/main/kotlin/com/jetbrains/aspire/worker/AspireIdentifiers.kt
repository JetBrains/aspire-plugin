package com.jetbrains.aspire.worker

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
@JvmInline
value class AspirePath(val value: String) {
    val fileName: String
        get() = value.substringAfterLast('/').substringAfterLast('\\')
}

@ApiStatus.Internal
@Serializable
@JvmInline
value class AspireAppHostId(val value: String)

@ApiStatus.Internal
@Serializable
data class AspireResourceId(val appHostId: AspireAppHostId, val resourceName: String)
