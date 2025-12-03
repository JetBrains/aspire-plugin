@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.dashboard

import com.intellij.execution.services.ServiceViewProvidingContributor
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.jetbrains.aspire.worker.AspireWorkerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn

internal class AspireWorkerViewModel(
    parentCs: CoroutineScope,
    workerService: AspireWorkerService,
) : ServiceViewProvidingContributor<AspireAppHostViewModel, AspireWorkerViewModel> {
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
                            add(AspireAppHostViewModel(cs, newAppHost))
                        }
                    }
                }.sortedBy { it.displayName }
            }
            .stateIn(cs, SharingStarted.Eagerly, emptyList())

    private val descriptor by lazy { AspireWorkerServiceViewDescriptor() }

    override fun getViewDescriptor(project: Project) = descriptor

    override fun asService(): AspireWorkerViewModel = this

    override fun getServiceDescriptor(
        project: Project,
        appHostViewModel: AspireAppHostViewModel
    ) = appHostViewModel.getViewDescriptor(project)

    override fun getServices(project: Project) = appHostViewModels.value
}