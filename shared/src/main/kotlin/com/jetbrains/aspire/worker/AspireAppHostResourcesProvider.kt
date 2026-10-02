package com.jetbrains.aspire.worker

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface AspireAppHostResourcesProvider {
    suspend fun getResources(appHostPath: AspireAppHostPath): List<AspireResourceData>
}
