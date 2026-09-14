package com.jetbrains.aspire.worker

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface AspireAppHostLifecycleManager {
    suspend fun launchAppHost(appHostId: AspireAppHostId, debug: Boolean)

    suspend fun stopAppHost(appHostId: AspireAppHostId)
}