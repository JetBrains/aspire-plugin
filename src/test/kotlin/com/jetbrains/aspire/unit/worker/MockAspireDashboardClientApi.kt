package com.jetbrains.aspire.unit.worker

import com.jetbrains.aspire.generated.dashboard.*
import com.jetbrains.aspire.worker.dashboard.AspireDashboardClientApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MockAspireDashboardClientApi : AspireDashboardClientApi {
    val resourceUpdates = MutableSharedFlow<WatchResourcesUpdate>(extraBufferCapacity = 64)
    private val consoleLogFlows = mutableMapOf<String, MutableSharedFlow<WatchResourceConsoleLogsUpdate>>()

    private val shutdownState = MutableStateFlow(false)
    val isShutdown: StateFlow<Boolean> = shutdownState.asStateFlow()

    override fun watchResources(): Flow<WatchResourcesUpdate> = resourceUpdates

    override fun watchResourceConsoleLogs(resourceName: String): Flow<WatchResourceConsoleLogsUpdate> {
        return consoleLogFlows.getOrPut(resourceName) { MutableSharedFlow() }
    }

    override suspend fun executeResourceCommand(request: ResourceCommandRequest): ResourceCommandResponse {
        return ResourceCommandResponse.getDefaultInstance()
    }

    override suspend fun getApplicationInformation(): ApplicationInformationResponse {
        return ApplicationInformationResponse.getDefaultInstance()
    }

    override fun shutdown() {
        shutdownState.value = true
    }
}
