@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.dashboard

import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.services.ServiceEventListener
import com.intellij.execution.services.ServiceViewProvidingContributor
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.TerminalExecutionConsoleBuilder
import com.jetbrains.aspire.worker.AspireResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class AspireResourceViewModel(
    private val project: Project,
    parentCs: CoroutineScope,
    val resource: AspireResource
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

    val uiState: StateFlow<ResourceUiState> =
        resource.resourceState
            .map { ResourceUiState(it, logConsole.component) }
            .stateIn(
                cs,
                SharingStarted.Lazily,
                ResourceUiState(resource.resourceState.value, logConsole.component)
            )

    private val childViewModels: StateFlow<List<AspireResourceViewModel>> =
        resource.childrenResources
            .runningFold(emptyList<AspireResourceViewModel>()) { currentViewModels, newResources ->
                val currentViewModelsByName = currentViewModels.associateBy { it.resource.resourceName }
                val newIds = newResources.map { it.resourceName }.toSet()

                buildList {
                    for (viewModel in currentViewModels) {
                        if (viewModel.resourceName in newIds) {
                            LOG.trace { "Resource ViewModel for ${viewModel.resourceName} already exists" }
                            add(viewModel)
                        } else {
                            LOG.trace { "Disposing Resource ViewModel for ${viewModel.resourceName}" }
                            Disposer.dispose(viewModel)
                        }
                    }

                    for (newResource in newResources) {
                        if (newResource.resourceName !in currentViewModelsByName) {
                            LOG.trace { "Creating new Resource ViewModel for ${newResource.resourceName}" }
                            val resourceVM = AspireResourceViewModel(project, cs, newResource)
                            Disposer.register(this@AspireResourceViewModel, resourceVM)
                            add(resourceVM)
                        }
                    }
                }.sortedWith(
                    compareBy(
                        { it.resource.resourceState.value.type },
                        { it.resource.resourceState.value.name })
                )
            }
            .stateIn(cs, SharingStarted.Eagerly, emptyList())

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

    override fun getViewDescriptor(project: Project) = descriptor

    override fun asService() = this

    override fun getServices(project: Project) = childViewModels.value

    override fun getServiceDescriptor(
        project: Project, vm: AspireResourceViewModel
    ) = vm.getViewDescriptor(project)

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
