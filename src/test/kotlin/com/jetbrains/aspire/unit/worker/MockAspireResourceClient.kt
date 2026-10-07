package com.jetbrains.aspire.unit.worker

import com.jetbrains.aspire.generated.dashboard.ResourceCommandRequest
import com.jetbrains.aspire.generated.dashboard.ResourceCommandResponse
import com.jetbrains.aspire.resources.AspireResourceClient
import com.jetbrains.aspire.resources.AspireResourceUpdate
import com.jetbrains.aspire.worker.AspireAppHostPath
import com.jetbrains.aspire.worker.AspireResourceLogEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class MockAspireResourceClient : AspireResourceClient {
    val resourceUpdates = MutableSharedFlow<AspireResourceUpdate>(extraBufferCapacity = 64)
    val watchedAppHostPaths = mutableListOf<AspireAppHostPath>()
    private val consoleLogFlows = mutableMapOf<String, MutableSharedFlow<List<AspireResourceLogEntry>>>()

    private val shutdownState = MutableStateFlow(false)
    val isShutdown: StateFlow<Boolean> = shutdownState.asStateFlow()

    override fun watchResources(appHostPath: AspireAppHostPath): Flow<AspireResourceUpdate> {
        watchedAppHostPaths.add(appHostPath)
        return resourceUpdates
    }

    override fun watchResourceConsoleLogs(resourceName: String): Flow<List<AspireResourceLogEntry>> {
        return consoleLogFlows.getOrPut(resourceName) { MutableSharedFlow() }
    }

    override suspend fun executeResourceCommand(request: ResourceCommandRequest): ResourceCommandResponse {
        return ResourceCommandResponse.getDefaultInstance()
    }

    override fun shutdown() {
        shutdownState.value = true
    }
}
