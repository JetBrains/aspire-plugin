package com.jetbrains.aspire.worker

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.Path

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
@JvmInline
value class AspireAppHostPath(val value: String)

@ApiStatus.Internal
fun AspireAppHostPath.toNioPath(): Path = Path(value)

@ApiStatus.Internal
@Serializable
data class AspireResourceId(val appHostPath: AspireAppHostPath, val resourceName: String)
