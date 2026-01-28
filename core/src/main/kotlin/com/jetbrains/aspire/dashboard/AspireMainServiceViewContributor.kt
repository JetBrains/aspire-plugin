package com.jetbrains.aspire.dashboard

import com.intellij.execution.services.ServiceViewContributor
import com.intellij.execution.services.ServiceViewLazyContributor
import com.intellij.execution.services.SimpleServiceViewDescriptor
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.AspireIcons
import com.jetbrains.aspire.worker.AspireWorker

class AspireMainServiceViewContributor : ServiceViewContributor<AspireWorkerViewModel>, ServiceViewLazyContributor {
    override fun getViewDescriptor(project: Project) =
        SimpleServiceViewDescriptor("Aspire", AspireIcons.Service)

    override fun getServices(project: Project): List<AspireWorkerViewModel> {
        if (AspireWorker.getInstance(project).appHosts.value.isEmpty()) return emptyList()

        val vm = AspireWorkerViewModelFactory.getInstance(project).create()
        return listOf(vm)
    }

    override fun getServiceDescriptor(project: Project, service: AspireWorkerViewModel) =
        service.getViewDescriptor(project)
}