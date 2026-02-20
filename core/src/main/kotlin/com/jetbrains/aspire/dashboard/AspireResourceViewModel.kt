@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.dashboard

import com.intellij.execution.services.ServiceEventListener
import com.intellij.execution.services.ServiceViewProvidingContributor
import com.intellij.openapi.Disposable
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
    val resource: AspireResource,
    private val project: Project,
    parentCs: CoroutineScope
) : ServiceViewProvidingContributor<AspireResourceViewModel, AspireResourceViewModel>, Disposable {
    private val cs = parentCs.childScope("Aspire Resource VM")

    private val descriptor by lazy { AspireResourceServiceViewDescriptor(this) }

    private val childViewModels: StateFlow<List<AspireResourceViewModel>> =
        resource.childrenResources
            .runningFold(emptyList<AspireResourceViewModel>()) { currentViewModels, newResources ->
                val currentById = currentViewModels.associateBy { it.resource.resourceId }
                val newIds = newResources.map { it.resourceId }.toSet()

                buildList {
                    for (newResource in newResources) {
                        val existing = currentById[newResource.resourceId]
                        if (existing != null) add(existing)
                        else {
                            val vm = AspireResourceViewModel(newResource, project, cs)
                            Disposer.register(this@AspireResourceViewModel, vm)
                            add(vm)
                        }
                    }
                }.sortedWith(compareBy({ it.resource.resourceState.value.type }, { it.resource.resourceState.value.name }))
            }
            .stateIn(cs, SharingStarted.Eagerly, emptyList())

    init {
        cs.launch {
            resource
                .resourceState
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
