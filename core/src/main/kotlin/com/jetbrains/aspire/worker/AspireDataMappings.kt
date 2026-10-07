package com.jetbrains.aspire.worker

import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

@ApiStatus.Internal
fun AspirePath.toNioPath(): Path = Path(value)

internal fun Path.toAspireAppHostPath(): AspireAppHostPath = AspireAppHostPath(absolutePathString())

@ApiStatus.Internal
fun AspireAppHost.toData(state: AspireAppHost.AspireAppHostState): AspireAppHostData {
    val status = when (state) {
        AspireAppHost.AspireAppHostState.Inactive -> AspireAppHostStatus.Inactive
        is AspireAppHost.AspireAppHostState.Started -> AspireAppHostStatus.Started
        AspireAppHost.AspireAppHostState.Stopped -> AspireAppHostStatus.Stopped
    }

    return AspireAppHostData(appHostPath, name, status)
}
