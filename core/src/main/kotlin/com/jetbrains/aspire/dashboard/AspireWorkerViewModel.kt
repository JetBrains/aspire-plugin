@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.dashboard

import com.intellij.execution.services.ServiceEventListener
import com.intellij.execution.services.ServiceViewProvidingContributor
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.jetbrains.aspire.worker.AspireWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AspireWorkerViewModel(
    private val project: Project,
    parentCs: CoroutineScope,
    workerService: AspireWorker,
) : ServiceViewProvidingContributor<AspireHost, AspireWorkerViewModel>, Disposable {
    private val cs: CoroutineScope = parentCs.childScope("Aspire Worker VM")

    private val appHostViewModels: StateFlow<List<AspireHost>> =
        workerService.appHosts
            .map { appHosts ->
                appHosts.sortedBy { it.displayName }
            }
            .stateIn(cs, SharingStarted.Eagerly, emptyList())

    private val descriptor by lazy { AspireWorkerServiceViewDescriptor() }

    init {
        cs.launch {
            appHostViewModels
                .runningFold(emptyList<AspireHost>() to emptyList<AspireHost>()) { (previousList, _), currentList ->
                    val previousPaths = previousList.map { it.hostProjectPath }.toSet()
                    val currentPaths = currentList.map { it.hostProjectPath }.toSet()

                    val added = currentList.filter { it.hostProjectPath !in previousPaths }
                    val removed = previousList.filter { it.hostProjectPath !in currentPaths }

                    currentList to (added + removed)
                }
                .drop(1)
                .collect { (currentList, changedViewModels) ->
                    val currentPaths = currentList.map { it.hostProjectPath }.toSet()

                    changedViewModels.forEach { viewModel ->
                        if (viewModel.hostProjectPath in currentPaths) {
                            sendServiceAddedEvent(viewModel)
                        } else {
                            sendServiceRemovedEvent(viewModel)
                        }
                    }
                }
        }
    }

    override fun getViewDescriptor(project: Project) = descriptor

    override fun asService(): AspireWorkerViewModel = this

    override fun getServiceDescriptor(
        project: Project,
        appHostViewModel: AspireHost
    ) = appHostViewModel.getViewDescriptor(project)

    override fun getServices(project: Project) = appHostViewModels.value

    private fun sendServiceAddedEvent(aspireAppHost: AspireHost) {
        val event = ServiceEventListener.ServiceEvent.createServiceAddedEvent(
            aspireAppHost,
            AspireMainServiceViewContributor::class.java,
            this
        )
        project.messageBus.syncPublisher(ServiceEventListener.TOPIC).handle(event)
    }

    private fun sendServiceRemovedEvent(aspireAppHost: AspireHost) {
        val event = ServiceEventListener.ServiceEvent.createEvent(
            ServiceEventListener.EventType.SERVICE_REMOVED,
            aspireAppHost,
            AspireMainServiceViewContributor::class.java
        )
        project.messageBus.syncPublisher(ServiceEventListener.TOPIC).handle(event)
    }

    override fun dispose() {
    }
}