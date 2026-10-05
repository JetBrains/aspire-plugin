package com.jetbrains.aspire.services

import com.intellij.execution.services.ServiceViewContributor
import com.intellij.execution.services.ServiceViewLazyContributor
import com.intellij.openapi.project.Project

internal class AspireMainServiceViewContributor : ServiceViewContributor<AspireWorkerViewModel>, ServiceViewLazyContributor {
    override fun getViewDescriptor(project: Project) = AspireMainServiceViewDescriptor

    override fun getServices(project: Project): List<AspireWorkerViewModel> {
        val vm = AspireWorkerViewModelManager.getInstance(project).getOrCreate()

        if (vm.getServices(project).isEmpty()) return emptyList()
        return listOf(vm)
    }

    override fun getServiceDescriptor(project: Project, service: AspireWorkerViewModel) =
        service.getViewDescriptor(project)
}
