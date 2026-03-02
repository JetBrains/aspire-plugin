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
import com.jetbrains.aspire.dashboard.ResourceListener
import com.jetbrains.aspire.generated.ResourceCommandResponseKind
import com.jetbrains.aspire.generated.ResourceLog
import com.jetbrains.aspire.generated.ResourceModel
import com.jetbrains.aspire.generated.ResourceState
import com.jetbrains.aspire.generated.dashboard.ResourceCommandRequest
import com.jetbrains.aspire.util.parseLogEntry
import com.google.protobuf.Value
import com.jetbrains.aspire.generated.dashboard.ResourceCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds

class AspireResource internal constructor(
    val resourceId: String,
    initialModel: ResourceModel,
    private val rawResourceType: String,
    private val dashboardClient: AspireDashboardClient,
    parentScope: CoroutineScope,
    private val project: Project
) : Disposable {
    companion object {
        private val LOG = logger<AspireResource>()
    }

    private val cs = parentScope.childScope("AspireResource $resourceId")

    private val _resourceState = MutableStateFlow(initialModel.toAspireResourceData())
    val resourceState: StateFlow<AspireResourceData> = _resourceState.asStateFlow()

    private val _childrenResources = MutableStateFlow<List<AspireResource>>(emptyList())
    val childrenResources: StateFlow<List<AspireResource>> = _childrenResources.asStateFlow()

    private val commandParameters = ConcurrentHashMap<String, Value>()

    private val resourceLogs = Channel<ResourceLog>(Channel.UNLIMITED)
    private val logProcessHandler = object : ProcessHandler() {
        override fun destroyProcessImpl() {}
        override fun detachProcessImpl() {}
        override fun detachIsDefault() = false
        override fun getProcessInput() = null
    }
    private val logConsole = TerminalExecutionConsoleBuilder(project)
        .build()
        .apply { attachToProcess(logProcessHandler) }
        .also { Disposer.register(this, it) }

    val logConsoleComponent
        get() = logConsole.component

    init {
        extractCommandParameters(initialModel)

        cs.launch {
            for (resourceLog in resourceLogs) {
                processResourceLog(resourceLog)
            }
        }

        cs.launch {
            dashboardClient.watchResourceConsoleLogs(initialModel.name)
                .retryWhen { cause, attempt ->
                    if (cause is CancellationException) {
                        false
                    } else {
                        val retryDelay = (500L * (1 shl attempt.coerceAtMost(6).toInt()))
                            .coerceAtMost(30_000L)
                        LOG.trace { "Log stream failed for $resourceId, retrying in ${retryDelay}ms (attempt ${attempt + 1}): ${cause.message}" }
                        delay(retryDelay.milliseconds)
                        true
                    }
                }
                .collect { update ->
                    for (logLine in update.logLinesList) {
                        resourceLogs.trySend(logLine.toResourceLog())
                    }
                }
        }

        logProcessHandler.startNotify()
    }

    internal fun update(model: ResourceModel) {
        extractCommandParameters(model)

        _resourceState.update {
            model.toAspireResourceData(it.state)
        }

        project.messageBus.syncPublisher(ResourceListener.TOPIC).resourceUpdated(this)
    }

    fun addChildResource(resource: AspireResource) {
        _childrenResources.update { it + resource }
    }

    fun removeChildResource(resource: AspireResource) {
        _childrenResources.update { it - resource }
    }

    suspend fun executeCommand(commandName: String) {
        val data = resourceState.value
        val requestBuilder = ResourceCommandRequest.newBuilder()
            .setCommandName(commandName)
            .setResourceName(data.name)
            .setResourceType(rawResourceType)
        val parameter = commandParameters[commandName]
        if (parameter != null) {
            requestBuilder.setParameter(parameter)
        }
        val request = requestBuilder.build()
        LOG.trace { "Executing command: $commandName for the resource $resourceId" }
        val response = dashboardClient.executeResourceCommand(request)
        if (response.kind != com.jetbrains.aspire.generated.dashboard.ResourceCommandResponseKind.RESOURCE_COMMAND_RESPONSE_KIND_SUCCEEDED) {
            LOG.warn("Unable to execute command: ${response.kind}, ${response.errorMessage}")
        }
    }

    private fun extractCommandParameters(model: ResourceModel) {
        // Command parameters are not available through the RD ResourceModel/ResourceCommand,
        // so they need to be extracted from proto commands separately.
        // This is handled by AspireAppHost passing proto commands during creation/update.
    }

    private fun processResourceLog(log: ResourceLog) {
        LOG.trace { "Received log: $log for the resource $resourceId" }
        val outputType = if (!log.isError) ProcessOutputTypes.STDOUT else ProcessOutputTypes.STDERR

        val (_, logContent) = parseLogEntry(log.text) ?: run {
            // In some situations (when receiving a huge multiline string in one go),
            // Aspire will send us all strings NOT prefixed by timestamp, but with line endings preserved.
            // Let's just trim those.
            null to log.text
        }

        logProcessHandler.notifyTextAvailable(logContent + "\r\n", outputType)
    }

    internal fun updateCommandParameters(protoCommands: List<ResourceCommand>) {
        for (command in protoCommands) {
            if (command.hasParameter()) {
                commandParameters[command.name] = command.parameter
            } else {
                commandParameters.remove(command.name)
            }
        }
    }

    override fun dispose() {
        cs.cancel()
    }
}
