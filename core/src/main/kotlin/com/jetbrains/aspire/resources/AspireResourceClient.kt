package com.jetbrains.aspire.resources

import com.jetbrains.aspire.generated.dashboard.ResourceCommandRequest
import com.jetbrains.aspire.generated.dashboard.ResourceCommandResponse
import com.jetbrains.aspire.generated.dashboard.WatchResourceConsoleLogsUpdate
import com.jetbrains.aspire.generated.dashboard.WatchResourcesUpdate
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface AspireResourceClient : AspireResourceWatcher, AspireResourceLogWatcher, AspireResourceCommandExecutor {
    fun shutdown()
}

@ApiStatus.Internal
interface AspireResourceWatcher {
    fun watchResources(): Flow<WatchResourcesUpdate>
}

@ApiStatus.Internal
interface AspireResourceLogWatcher {
    fun watchResourceConsoleLogs(resourceName: String): Flow<WatchResourceConsoleLogsUpdate>
}

@ApiStatus.Internal
interface AspireResourceCommandExecutor {
    suspend fun executeResourceCommand(request: ResourceCommandRequest): ResourceCommandResponse
}