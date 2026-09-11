package com.jetbrains.aspire.worker

import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

@ApiStatus.Internal
fun AspirePath.toNioPath(): Path = Path(value)

internal fun Path.toAspireAppHostId(): AspireAppHostId = AspireAppHostId(absolutePathString())

@ApiStatus.Internal
fun AspireAppHost.toData(): AspireAppHostData {
    val state = appHostState.value
    val status = when (state) {
        AspireAppHost.AspireAppHostState.Inactive -> AspireAppHostStatus.Inactive
        is AspireAppHost.AspireAppHostState.Starting -> AspireAppHostStatus.Starting
        is AspireAppHost.AspireAppHostState.Started -> AspireAppHostStatus.Started
        AspireAppHost.AspireAppHostState.Stopped -> AspireAppHostStatus.Stopped
    }
    val dashboardUrl = when (state) {
        AspireAppHost.AspireAppHostState.Inactive,
        AspireAppHost.AspireAppHostState.Stopped -> null

        is AspireAppHost.AspireAppHostState.Starting -> state.environment.aspireHostProjectUrl
        is AspireAppHost.AspireAppHostState.Started -> state.environment.aspireHostProjectUrl
    }

    return AspireAppHostData(
        id = mainFilePath.toAspireAppHostId(),
        name = name,
        status = status,
        dashboardUrl = dashboardUrl,
    )
}
