@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.dashboard

import com.intellij.execution.services.ServiceViewProvidingContributor
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.jetbrains.aspire.worker.AspireWorkerService
import kotlinx.coroutines.CoroutineScope

internal class AspireWorkerViewModel(
    private val project: Project,
    parentCs: CoroutineScope,
    private val workerService: AspireWorkerService,
) : ServiceViewProvidingContributor<AspireAppHostViewModel, AspireWorkerViewModel> {
    private val cs: CoroutineScope = parentCs.childScope("Aspire Worker VM")

    private val descriptor by lazy { AspireWorkerServiceViewDescriptor() }

    override fun getViewDescriptor(project: Project) = descriptor

    override fun asService(): AspireWorkerViewModel = this

    override fun getServiceDescriptor(
        project: Project,
        appHostViewModel: AspireAppHostViewModel
    ) = appHostViewModel.getViewDescriptor(project)

    override fun getServices(project: Project): List<AspireAppHostViewModel?> {
        TODO("Not yet implemented")
    }
}