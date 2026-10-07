package com.jetbrains.aspire.resources

import com.jetbrains.aspire.worker.AspireAppHostPath
import com.jetbrains.aspire.worker.AspireResourceLogEntry
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface AspireResourceClient : AspireResourceWatcher, AspireResourceLogWatcher, AspireResourceCommandExecutor {
    fun shutdown()
}

@ApiStatus.Internal
interface AspireResourceWatcher {
    fun watchResources(appHostPath: AspireAppHostPath): Flow<AspireResourceUpdate>
}

@ApiStatus.Internal
interface AspireResourceLogWatcher {
    fun watchResourceConsoleLogs(resourceName: String): Flow<List<AspireResourceLogEntry>>
}

@ApiStatus.Internal
interface AspireResourceCommandExecutor {
    suspend fun executeResourceCommand(request: AspireResourceCommandRequest): AspireResourceCommandResponse
}
