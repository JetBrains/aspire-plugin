@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.worker

import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.TerminalExecutionConsoleBuilder
import com.jetbrains.aspire.generated.dashboard.ConsoleLogLine
import com.jetbrains.aspire.generated.dashboard.ResourceCommandRequest
import com.jetbrains.aspire.generated.dashboard.ResourceCommandResponse
import com.jetbrains.aspire.generated.dashboard.ResourceCommandResponseKind
import com.jetbrains.aspire.util.parseLogEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.cancellation.CancellationException

/**
 * Represents a single resource running within an Aspire AppHost.
 *
 * Key responsibilities:
 * - Tracking the resource's current state via [resourceState] (type, status, properties, endpoints, etc.)
 * - Collecting and displaying resource console logs through a [LogProcessHandler] and terminal console
 * - Executing resource commands (start, stop, restart) via the dashboard gRPC client
 * - Managing child resources in a parent-child tree structure ([childrenResources])
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
    project: Project,
    parentCs: CoroutineScope,
    private val dashboardClient: AspireDashboardClientApi,
) : Disposable {
    companion object {
        private val LOG = logger<AspireResource>()
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

    private val logProcessHandler = LogProcessHandler()
    private val logConsole = TerminalExecutionConsoleBuilder(project)
        .build()
        .apply { attachToProcess(logProcessHandler) }
        .also { Disposer.register(this, it) }

    val logConsoleComponent
        get() = logConsole.component

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

        logProcessHandler.startNotify()
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
        val outputType = if (!log.isStdErr) ProcessOutputTypes.STDOUT else ProcessOutputTypes.STDERR

        val (_, logContent) = parseLogEntry(log.text) ?: run {
            // In some situations (when receiving a huge multiline string in one go),
            // Aspire will send us all strings NOT prefixed by timestamp, but with line endings preserved.
            // Let's just trim those.
            null to log.text
        }

        logProcessHandler.notifyTextAvailable(logContent + "\r\n", outputType)
    }

    override fun dispose() {
        cs.cancel()
    }

    /**
     * A no-op [ProcessHandler] used solely as a sink for resource console log output.
     *
     * The terminal console requires a [ProcessHandler] to attach to; this implementation
     * provides one that does not manage any real process.
     */
    private class LogProcessHandler : ProcessHandler() {
        override fun destroyProcessImpl() {}
        override fun detachProcessImpl() {}
        override fun detachIsDefault() = false
        override fun getProcessInput() = null
    }
}
