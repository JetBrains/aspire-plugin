@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.services

import com.intellij.execution.services.ServiceEventListener
import com.intellij.execution.services.ServiceViewProvidingContributor
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.jetbrains.aspire.worker.AspireServicesModelProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

internal class AspireWorkerViewModel(
    private val project: Project,
    parentCs: CoroutineScope,
    servicesModelProvider: AspireServicesModelProvider,
) : ServiceViewProvidingContributor<AspireAppHostViewModel, AspireWorkerViewModel>, Disposable {
    companion object {
        private val LOG = logger<AspireWorkerViewModel>()
    }

    private val cs: CoroutineScope = parentCs.childScope("Aspire Worker VM")

    private val appHostViewModels: StateFlow<List<AspireAppHostViewModel>> =
        servicesModelProvider.appHosts
            .runningFold(emptyList<AspireAppHostViewModel>()) { currentViewModels, newAppHosts ->
                val currentIds = currentViewModels.associateBy { it.appHostId }
                val newIds = newAppHosts.map { it.appHostId }.toSet()

                buildList {
                    for (viewModel in currentViewModels) {
                        if (viewModel.appHostId in newIds) {
                            LOG.trace { "AppHost ViewModel for ${viewModel.appHostId.value} already exists" }
                            add(viewModel)
                        }
                    }

                    for (newAppHost in newAppHosts) {
                        if (newAppHost.appHostId !in currentIds) {
                            LOG.trace { "Creating new AppHost ViewModel for ${newAppHost.appHostId.value}" }
                            val appHostVM = AspireAppHostViewModel(project, cs, newAppHost)
                            if (Disposer.tryRegister(this@AspireWorkerViewModel, appHostVM)) {
                                add(appHostVM)
                            }
                        }
                    }
                }.sortedBy { it.displayName }
            }
            .stateIn(cs, SharingStarted.Eagerly, emptyList())

    private val descriptor by lazy { AspireWorkerServiceViewDescriptor() }

    init {
        cs.launch {
            appHostViewModels
                .runningFold(emptyList<AspireAppHostViewModel>() to emptyList<AspireAppHostViewModel>()) { (previousList, _), currentList ->
                    if ((previousList.isEmpty() && currentList.isNotEmpty()) || (currentList.isEmpty() && previousList.isNotEmpty())) {
                        sendResetEvent()
                    }

                    val previousIds = previousList.map { it.appHostId }.toSet()
                    val currentIds = currentList.map { it.appHostId }.toSet()

                    val added = currentList.filter { it.appHostId !in previousIds }
                    val removed = previousList.filter { it.appHostId !in currentIds }

                    LOG.trace { "ViewModel collection was changed:" }
                    LOG.trace { "Added ${added.map { it.appHostId.value }.joinToString()}" }
                    LOG.trace { "Removed ${removed.map { it.appHostId.value }.joinToString()}" }

                    currentList to (added + removed)
                }
                .drop(1)
                .collect { (currentList, changedViewModels) ->
                    val currentIds = currentList.map { it.appHostId }.toSet()

                    changedViewModels.forEach { viewModel ->
                        if (viewModel.appHostId in currentIds) {
                            sendServiceAddedEvent(viewModel)
                        } else {
                            sendServiceRemovedEvent(viewModel)
                            LOG.trace { "Disposing AppHost ViewModel for ${viewModel.appHostId.value}" }
                            Disposer.dispose(viewModel)
                        }
                    }
                }
        }
    }

    override fun getViewDescriptor(project: Project) = descriptor

    override fun asService(): AspireWorkerViewModel = this

    override fun getServiceDescriptor(
        project: Project,
        appHostViewModel: AspireAppHostViewModel
    ) = appHostViewModel.getViewDescriptor(project)

    override fun getServices(project: Project) = appHostViewModels.value

    private fun sendResetEvent() {
        val event = ServiceEventListener.ServiceEvent.createResetEvent(
            AspireMainServiceViewContributor::class.java,
        )
        project.messageBus.syncPublisher(ServiceEventListener.TOPIC).handle(event)
    }

    private fun sendServiceAddedEvent(appHostViewModel: AspireAppHostViewModel) {
        val event = ServiceEventListener.ServiceEvent.createServiceAddedEvent(
            appHostViewModel,
            AspireMainServiceViewContributor::class.java,
            this
        )
        project.messageBus.syncPublisher(ServiceEventListener.TOPIC).handle(event)
    }

    private fun sendServiceRemovedEvent(appHostViewModel: AspireAppHostViewModel) {
        val event = ServiceEventListener.ServiceEvent.createEvent(
            ServiceEventListener.EventType.SERVICE_REMOVED,
            appHostViewModel,
            AspireMainServiceViewContributor::class.java
        )
        project.messageBus.syncPublisher(ServiceEventListener.TOPIC).handle(event)
    }

    override fun dispose() {
        LOG.trace { "Disposing AspireWorker VM" }
        cs.cancel()
    }
}
