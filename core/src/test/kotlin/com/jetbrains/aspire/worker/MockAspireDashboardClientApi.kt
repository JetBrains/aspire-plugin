package com.jetbrains.aspire.worker

import com.jetbrains.aspire.generated.dashboard.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class MockAspireDashboardClientApi : AspireDashboardClientApi {
    val resourceUpdates = MutableSharedFlow<WatchResourcesUpdate>(extraBufferCapacity = 64)
    private val consoleLogFlows = mutableMapOf<String, MutableSharedFlow<WatchResourceConsoleLogsUpdate>>()

    var isShutdown = false
        private set

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
        isShutdown = true
    }
}
