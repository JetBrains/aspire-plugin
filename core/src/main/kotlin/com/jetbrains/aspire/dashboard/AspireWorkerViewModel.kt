@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.dashboard

import com.intellij.execution.services.ServiceEventListener
import com.intellij.execution.services.ServiceViewProvidingContributor
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.jetbrains.aspire.worker.AspireWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class AspireWorkerViewModel(
    private val project: Project,
    parentCs: CoroutineScope,
    workerService: AspireWorker,
) : ServiceViewProvidingContributor<AspireAppHostViewModel, AspireWorkerViewModel>, Disposable {
    private val cs: CoroutineScope = parentCs.childScope("Aspire Worker VM")

    private val appHostViewModels: StateFlow<List<AspireAppHostViewModel>> =
        workerService.appHosts
            .runningFold(emptyList<AspireAppHostViewModel>()) { currentViewModels, newAppHosts ->
                val currentPaths = currentViewModels.associateBy { it.appHostMainFilePath }
                val newPaths = newAppHosts.map { it.mainFilePath }.toSet()

                buildList {
                    for (viewModel in currentViewModels) {
                        if (viewModel.appHostMainFilePath in newPaths) {
                            add(viewModel)
                        }
                    }

                    for (newAppHost in newAppHosts) {
                        if (newAppHost.mainFilePath !in currentPaths) {
                            val appHostVM = AspireAppHostViewModel(project, cs, newAppHost)
                            Disposer.register(this@AspireWorkerViewModel, appHostVM)
                            add(appHostVM)
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
                    val previousPaths = previousList.map { it.appHostMainFilePath }.toSet()
                    val currentPaths = currentList.map { it.appHostMainFilePath }.toSet()

                    val added = currentList.filter { it.appHostMainFilePath !in previousPaths }
                    val removed = previousList.filter { it.appHostMainFilePath !in currentPaths }

                    currentList to (added + removed)
                }
                .drop(1)
                .collect { (currentList, changedViewModels) ->
                    val currentPaths = currentList.map { it.appHostMainFilePath }.toSet()

                    changedViewModels.forEach { viewModel ->
                        if (viewModel.appHostMainFilePath in currentPaths) {
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
        appHostViewModel: AspireAppHostViewModel
    ) = appHostViewModel.getViewDescriptor(project)

    override fun getServices(project: Project) = appHostViewModels.value

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
    }
}