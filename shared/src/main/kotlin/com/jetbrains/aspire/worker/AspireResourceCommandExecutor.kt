package com.jetbrains.aspire.worker

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface AspireResourceCommandExecutor {
    suspend fun executeCommand(resourceId: AspireResourceId, commandName: String)
}
