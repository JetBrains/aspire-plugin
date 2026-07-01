@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.worker

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.platform.util.coroutines.childScope
import com.jetbrains.aspire.generated.dashboard.ConsoleLogLine
import com.jetbrains.aspire.generated.dashboard.ResourceCommandRequest
import com.jetbrains.aspire.generated.dashboard.ResourceCommandResponse
import com.jetbrains.aspire.generated.dashboard.ResourceCommandResponseKind
import com.jetbrains.aspire.util.parseLogEntry
import com.jetbrains.aspire.worker.dashboard.AspireDashboardClientApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.cancellation.CancellationException

/**
 * Represents a single resource running within an Aspire AppHost.
 *
 * Key responsibilities:
 * - Tracking the resource's current state via [resourceState] (type, status, properties, endpoints, etc.)
 * - Collecting resource console logs into a bounded in-memory buffer exposed as [logFlow]
 * - Executing resource commands (start, stop, restart) via the dashboard gRPC client
 * - Managing child resources in a parent-child tree structure ([childrenResources])
 *
 * Log lines are kept in a [MutableSharedFlow] with a fixed replay capacity, so a lazily created
 * UI consumer (e.g. an [com.jetbrains.aspire.dashboard.AspireResourceViewModel]) receives the most
 * recent history immediately, then continues to receive live emissions. Older entries are dropped
 * once the replay capacity is exceeded to bound memory.
 *
 * Instances are created and managed exclusively by [ResourceTreeManager]. The [update] method
 * is internal and should only be called by the owning resource manager to apply state changes
 * received from the dashboard.
 *
 * @param resourceName the unique identifier of this resource within the AppHost
 * @param initialData the initial state data for this resource
 *
 * @see ResourceTreeManager for resource lifecycle management
 * @see AspireAppHost for the parent AppHost
 */
@ApiStatus.Internal
class AspireResource(
    val resourceName: String,
    initialData: AspireResourceData,
    parentCs: CoroutineScope,
    private val dashboardClient: AspireDashboardClientApi,
) : Disposable {
    companion object {
        private val LOG = logger<AspireResource>()
        private const val LOG_REPLAY_CAPACITY = 500
    }

    private val cs = parentCs.childScope("Aspire Resource")

    private val _resourceState = MutableStateFlow(initialData)
    val resourceState: StateFlow<AspireResourceData> = _resourceState.asStateFlow()

    val displayName: String
        get() = _resourceState.value.displayName

    val parentDisplayName: String?
        get() = _resourceState.value.parentDisplayName

    private val _childrenResources = MutableStateFlow<List<AspireResource>>(emptyList())
    val childrenResources: StateFlow<List<AspireResource>> = _childrenResources.asStateFlow()

    private val _logFlow = MutableSharedFlow<AspireResourceLogEntry>(
        replay = LOG_REPLAY_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val logFlow: SharedFlow<AspireResourceLogEntry> = _logFlow.asSharedFlow()

    init {
        cs.launch {
            dashboardClient.watchResourceConsoleLogs(resourceName)
                .catch { cause ->
                    if (cause is CancellationException) throw cause
                    LOG.trace { "Console log stream for $resourceName ended: ${cause.message}" }
                }
                .collect { update ->
                    for (logLine in update.logLinesList) {
                        if (logLine.text.isEmpty()) continue
                        processResourceLog(logLine)
                    }
                }
        }
    }

    /**
     * Updates the resource state with new data from the dashboard.
     *
     * This method is internal and should only be called by [ResourceTreeManager]
     * when processing resource upsert events from the gRPC stream.
     */
    internal fun update(data: AspireResourceData) {
        _resourceState.value = data
    }

    fun addChildResource(resource: AspireResource) {
        _childrenResources.update { current ->
            if (resource in current) current
            else current + resource
        }
    }

    fun removeChildResource(resource: AspireResource) {
        _childrenResources.update { it - resource }
    }

    suspend fun executeCommand(commandName: String): ResourceCommandResponse {
        val request = ResourceCommandRequest.newBuilder()
            .setCommandName(commandName)
            .setResourceName(resourceName)
            .setResourceType(_resourceState.value.originType)
            .build()
        val response = dashboardClient.executeResourceCommand(request)
        if (response.kind == ResourceCommandResponseKind.RESOURCE_COMMAND_RESPONSE_KIND_FAILED) {
            LOG.warn("Command $commandName on $resourceName failed: ${response.errorMessage}")
        }

        return response
    }

    private fun processResourceLog(log: ConsoleLogLine) {
        LOG.trace { "Received log: $log for the resource $resourceName" }

        val (_, logContent) = parseLogEntry(log.text) ?: run {
            // In some situations (when receiving a huge multiline string in one go),
            // Aspire will send us all strings NOT prefixed by timestamp, but with line endings preserved.
            // Let's just trim those.
            null to log.text
        }

        _logFlow.tryEmit(AspireResourceLogEntry(logContent, log.isStdErr))
    }

    override fun dispose() {
        LOG.trace { "Disposing AspireResource for $resourceName" }
        cs.cancel()
    }
}
