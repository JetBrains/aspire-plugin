@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.services

import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.services.ServiceEventListener
import com.intellij.execution.services.ServiceViewDescriptor
import com.intellij.execution.services.ServiceViewProvidingContributor
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.TerminalExecutionConsoleBuilder
import com.jetbrains.aspire.worker.AspireResourceModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class AspireResourceViewModel(
    private val project: Project,
    parentCs: CoroutineScope,
    val resource: AspireResourceModel
) : ServiceViewProvidingContributor<AspireResourceViewModel, AspireResourceViewModel>, Disposable {
    companion object {
        private val LOG = logger<AspireResourceViewModel>()
    }

    private val cs = parentCs.childScope("Aspire Resource VM")

    private val descriptor by lazy { AspireResourceServiceViewDescriptor(this) }

    val resourceName: String = resource.resourceName

    private val logProcessHandler = LogProcessHandler()
    private val logConsole = TerminalExecutionConsoleBuilder(project)
        .build()
        .apply { attachToProcess(logProcessHandler) }
        .also { Disposer.register(this, it) }

    internal val uiState: StateFlow<ResourceUiState> =
        resource.data
            .map { ResourceUiState(it, logConsole.component) }
            .stateIn(
                cs,
                SharingStarted.Lazily,
                ResourceUiState(resource.data.value, logConsole.component)
            )

    private val childViewModels: StateFlow<List<AspireResourceViewModel>> =
        resource.childrenResources
            .toResourceViewModels(project, cs, this)

    init {
        logProcessHandler.startNotify()

        cs.launch {
            resource.logFlow.collect { entry ->
                val outputType = if (entry.isStdErr) ProcessOutputTypes.STDERR else ProcessOutputTypes.STDOUT
                logProcessHandler.notifyTextAvailable(entry.text + "\r\n", outputType)
            }
        }

        cs.launch {
            uiState
                .drop(1)
                .collect { sendServiceChangedEvent() }
        }

        cs.launch {
            childViewModels
                .drop(1)
                .collect { sendServiceChildrenChangedEvent() }
        }
    }

    override fun getViewDescriptor(project: Project): ServiceViewDescriptor = descriptor

    override fun asService() = this

    override fun getServices(project: Project) = childViewModels.value

    override fun getServiceDescriptor(
        project: Project, vm: AspireResourceViewModel
    ): ServiceViewDescriptor = vm.getViewDescriptor(project)

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
        LOG.trace { "Disposing AspireResource VM for $resourceName" }
        cs.cancel()
    }
}
