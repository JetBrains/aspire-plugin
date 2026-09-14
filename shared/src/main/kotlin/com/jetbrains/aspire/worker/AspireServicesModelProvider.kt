package com.jetbrains.aspire.worker

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface AspireServicesModelProvider {
    val appHosts: StateFlow<List<AspireAppHostModel>>
}

@ApiStatus.Internal
interface AspireAppHostModel {
    val appHostId: AspireAppHostId
    val data: StateFlow<AspireAppHostData>
    val rootResources: StateFlow<List<AspireResourceModel>>
    val logFlow: StateFlow<SharedFlow<AppHostLogEntry>?>
}

@ApiStatus.Internal
interface AspireResourceModel {
    val resourceName: String
    val data: StateFlow<AspireResourceData>
    val childrenResources: StateFlow<List<AspireResourceModel>>
    val logFlow: SharedFlow<AspireResourceLogEntry>
}
