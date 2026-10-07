package com.jetbrains.aspire.resources

import com.jetbrains.aspire.worker.AspireResourceData
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
sealed interface AspireResourceUpdate {
    data class InitialData(val resources: List<AspireResourceData>) : AspireResourceUpdate

    data class Changes(val changes: List<AspireResourceChange>) : AspireResourceUpdate
}

@ApiStatus.Internal
sealed interface AspireResourceChange {
    data class Upsert(val data: AspireResourceData) : AspireResourceChange

    data class Delete(val resourceName: String) : AspireResourceChange
}
