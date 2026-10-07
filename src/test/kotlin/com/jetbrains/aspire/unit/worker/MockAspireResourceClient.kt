package com.jetbrains.aspire.unit.worker

import com.jetbrains.aspire.resources.AspireResourceClient
import com.jetbrains.aspire.resources.AspireResourceUpdate
import com.jetbrains.aspire.worker.AspireAppHostPath
import com.jetbrains.aspire.worker.AspireResourceCommandRequest
import com.jetbrains.aspire.worker.AspireResourceCommandResponse
import com.jetbrains.aspire.worker.AspireResourceLogEntry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.updateAndGet
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

internal class MockAspireResourceClient : AspireResourceClient {
    val resourceUpdates = MutableSharedFlow<AspireResourceUpdate>(extraBufferCapacity = 64)

    val watchedAppHostPaths: MutableList<AspireAppHostPath> = Collections.synchronizedList(mutableListOf())
    val watchedResourceNames: MutableList<String> = Collections.synchronizedList(mutableListOf())
    val commandRequests: MutableList<AspireResourceCommandRequest> = Collections.synchronizedList(mutableListOf())

    private val consoleLogFlows = MutableStateFlow<Map<String, MutableSharedFlow<List<AspireResourceLogEntry>>>>(emptyMap())
    val consoleLogSubscriptionCount: Int get() = consoleLogFlows.value.values.sumOf { it.subscriptionCount.value }

    @OptIn(ExperimentalCoroutinesApi::class)
    val consoleLogSubscriptionCountFlow: Flow<Int> = consoleLogFlows.flatMapLatest { flows ->
        if (flows.isEmpty()) flowOf(0)
        else combine(flows.values.map { it.subscriptionCount }) { counts -> counts.sum() }
    }

    @Volatile
    var resourceSubscriptionsAtShutdown: Int? = null
        private set
    @Volatile
    var consoleLogSubscriptionsAtShutdown: Int? = null
        private set
    @Volatile
    var onShutdown: (() -> Unit)? = null

    private val shutdownState = MutableStateFlow(false)
    val isShutdown: StateFlow<Boolean> = shutdownState.asStateFlow()

    private val shutdownCalls = AtomicInteger()
    val shutdownCount: Int
        get() = shutdownCalls.get()

    override fun watchResources(appHostPath: AspireAppHostPath): Flow<AspireResourceUpdate> {
        watchedAppHostPaths.add(appHostPath)
        return resourceUpdates
    }

    override fun watchResourceConsoleLogs(resourceName: String): Flow<List<AspireResourceLogEntry>> {
        watchedResourceNames.add(resourceName)
        return consoleLogFlows.updateAndGet { flows ->
            if (resourceName in flows) flows else flows + (resourceName to MutableSharedFlow())
        }.getValue(resourceName)
    }

    override suspend fun executeResourceCommand(request: AspireResourceCommandRequest): AspireResourceCommandResponse {
        commandRequests.add(request)
        return AspireResourceCommandResponse()
    }

    override fun shutdown() {
        resourceSubscriptionsAtShutdown = resourceUpdates.subscriptionCount.value
        consoleLogSubscriptionsAtShutdown = consoleLogSubscriptionCount
        onShutdown?.invoke()
        shutdownCalls.incrementAndGet()
        shutdownState.value = true
    }
}
