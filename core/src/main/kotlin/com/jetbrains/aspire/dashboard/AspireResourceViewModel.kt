@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.dashboard

import com.intellij.execution.services.ServiceEventListener
import com.intellij.execution.services.ServiceViewProvidingContributor
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.jetbrains.aspire.worker.AspireResource
import kotlinx.coroutines.CoroutineScope
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

    val resourceId: String = resource.resourceId

    val uiState: StateFlow<ResourceUiState> =
        resource.resourceState
            .map { ResourceUiState(it, resource.logConsoleComponent) }
            .stateIn(
                cs,
                SharingStarted.Lazily,
                ResourceUiState(resource.resourceState.value, resource.logConsoleComponent)
            )

    private val childViewModels: StateFlow<List<AspireResourceViewModel>> =
        resource.childrenResources
            .runningFold(emptyList<AspireResourceViewModel>()) { currentViewModels, newResources ->
                val currentViewModelsById = currentViewModels.associateBy { it.resource.resourceId }
                val newIds = newResources.map { it.resourceId }.toSet()

                buildList {
                    for (viewModel in currentViewModels) {
                        if (viewModel.resourceId in newIds) {
                            LOG.trace { "Resource ViewModel for ${viewModel.resourceId} already exists" }
                            add(viewModel)
                        }
                    }

                    for (newResource in newResources) {
                        if (newResource.resourceId !in currentViewModelsById) {
                            LOG.trace { "Creating new Resource ViewModel for ${newResource.resourceId}" }
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
    }
}
