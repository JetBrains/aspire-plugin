package com.jetbrains.aspire.worker

import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.TerminalExecutionConsoleBuilder
import com.jetbrains.aspire.dashboard.ResourceListener
import com.jetbrains.aspire.generated.ResourceCommandRequest
import com.jetbrains.aspire.generated.ResourceCommandResponseKind
import com.jetbrains.aspire.generated.ResourceLog
import com.jetbrains.aspire.generated.ResourceModel
import com.jetbrains.aspire.generated.ResourceWrapper
import com.jetbrains.aspire.util.parseLogEntry
import com.jetbrains.rd.util.lifetime.Lifetime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AspireResource(
    val resourceId: String,
    private val modelWrapper: ResourceWrapper,
    val lifetime: Lifetime,
    private val project: Project
) : Disposable {
    companion object {
        private val LOG = logger<AspireResource>()
    }

    private val _resourceState = MutableStateFlow(modelWrapper.model.valueOrNull.toAspireResourceData())
    val resourceState: StateFlow<AspireResourceData> = _resourceState.asStateFlow()

    private val _childrenResources = MutableStateFlow<List<AspireResource>>(emptyList())
    val childrenResources: StateFlow<List<AspireResource>> = _childrenResources.asStateFlow()

    private val resourceLogs = Channel<ResourceLog>(Channel.Factory.UNLIMITED)
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
        lifetime.coroutineScope.launch {
            for (resourceLog in resourceLogs) {
                processResourceLog(resourceLog)
            }
        }

        modelWrapper.model.advise(lifetime, ::update)
        modelWrapper.logReceived.advise(lifetime, ::logReceived)

        logProcessHandler.startNotify()
    }

    private fun update(model: ResourceModel) {
        _resourceState.update {
            model.toAspireResourceData(it.state)
        }

        project.messageBus.syncPublisher(ResourceListener.Companion.TOPIC).resourceUpdated(this)
    }

    fun addChildResource(resource: AspireResource) {
        _childrenResources.update { it + resource }
    }

    fun removeChildResource(resource: AspireResource) {
        _childrenResources.update { it - resource }
    }

    suspend fun executeCommand(commandName: String) = withContext(Dispatchers.EDT) {
        val data = resourceState.value
        val command = ResourceCommandRequest(
            commandName,
            data.name,
            data.type.toString()
        )
        LOG.trace { "Executing command: $command for the resource $resourceId" }
        val response = modelWrapper.executeCommand.startSuspending(command)
        if (response.kind != ResourceCommandResponseKind.Succeeded) {
            LOG.warn("Unable to execute command: ${response.kind}, ${response.errorMessage}")
        }
    }

    private fun logReceived(log: ResourceLog) {
        resourceLogs.trySend(log)
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

    override fun dispose() {
    }
}