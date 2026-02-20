package com.jetbrains.aspire.dashboard

import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.services.ServiceEventListener
import com.intellij.execution.services.ServiceViewProvidingContributor
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.TerminalExecutionConsoleBuilder
import com.jetbrains.aspire.generated.*
import com.jetbrains.aspire.util.parseLogEntry
import com.jetbrains.aspire.worker.AspireResourceData
import com.jetbrains.aspire.worker.toAspireResourceData
import com.jetbrains.rd.util.lifetime.Lifetime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AspireResource(
    val resourceId: String,
    private val modelWrapper: ResourceWrapper,
    val lifetime: Lifetime,
    private val project: Project
) : ServiceViewProvidingContributor<AspireResource, AspireResource>, Disposable {
    companion object {
        private val LOG = logger<AspireResource>()
    }

    var data: AspireResourceData
        private set

    val parentResourceName: String?
        get() = data.parentResourceName

    private val _childrenResources = MutableStateFlow<List<AspireResource>>(emptyList())
    val childrenResources: StateFlow<List<AspireResource>> = _childrenResources.asStateFlow()

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

    private val descriptor by lazy { AspireResourceServiceViewDescriptor(this) }

    init {
        val model = modelWrapper.model.valueOrNull
        data = model.toAspireResourceData()

        lifetime.coroutineScope.launch {
            for (resourceLog in resourceLogs) {
                processResourceLog(resourceLog)
            }
        }

        lifetime.coroutineScope.launch {
            _childrenResources
                .drop(1)
                .collect {
                    sendServiceChildrenChangedEvent()
                }
        }

        modelWrapper.model.advise(lifetime, ::update)
        modelWrapper.logReceived.advise(lifetime, ::logReceived)

        logProcessHandler.startNotify()
    }

    override fun asService() = this

    override fun getViewDescriptor(project: Project) = descriptor

    override fun getServices(project: Project) = childrenResources.value

    override fun getServiceDescriptor(project: Project, aspireResource: AspireResource) =
        aspireResource.getViewDescriptor(project)

    private fun update(model: ResourceModel) {
        data = model.toAspireResourceData(data.state)

        project.messageBus.syncPublisher(ResourceListener.TOPIC).resourceUpdated(this)

        sendServiceChangedEvent()
    }

    fun addChildResource(resource: AspireResource) {
        _childrenResources.update { it + resource }
    }

    fun removeChildResource(resource: AspireResource) {
        _childrenResources.update { it - resource }
    }

    suspend fun executeCommand(commandName: String) = withContext(Dispatchers.EDT) {
        val command = ResourceCommandRequest(
            commandName,
            data.name,
            data.type.toString()
        )
        LOG.trace { "Executing command: $command for the resource ${data.uid}" }
        val response = modelWrapper.executeCommand.startSuspending(command)
        if (response.kind != ResourceCommandResponseKind.Succeeded) {
            LOG.warn("Unable to execute command: ${response.kind}, ${response.errorMessage}")
        }
    }

    private fun logReceived(log: ResourceLog) {
        resourceLogs.trySend(log)
    }

    private fun processResourceLog(log: ResourceLog) {
        LOG.trace { "Received log: $log for the resource ${data.uid}" }
        val outputType = if (!log.isError) ProcessOutputTypes.STDOUT else ProcessOutputTypes.STDERR

        val (_, logContent) = parseLogEntry(log.text) ?: run {
            // In some situations (when receiving a huge multiline string in one go),
            // Aspire will send us all strings NOT prefixed by timestamp, but with line endings preserved.
            // Let's just trim those.
            null to log.text
        }

        logProcessHandler.notifyTextAvailable(logContent + "\r\n", outputType)
    }

    private fun sendServiceChangedEvent() {
        val event = ServiceEventListener.ServiceEvent.createEvent(
            ServiceEventListener.EventType.SERVICE_CHANGED,
            this,
            AspireMainServiceViewContributor::class.java
        )
        project.messageBus.syncPublisher(ServiceEventListener.TOPIC).handle(event)
    }

    private fun sendServiceChildrenChangedEvent() {
        val event = ServiceEventListener.ServiceEvent.createEvent(
            ServiceEventListener.EventType.SERVICE_CHILDREN_CHANGED,
            this,
            AspireMainServiceViewContributor::class.java
        )
        project.messageBus.syncPublisher(ServiceEventListener.TOPIC).handle(event)
    }

    override fun dispose() {
    }
}
