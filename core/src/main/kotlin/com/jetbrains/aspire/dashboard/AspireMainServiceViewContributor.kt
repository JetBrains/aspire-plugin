package com.jetbrains.aspire.dashboard

import com.intellij.execution.services.ServiceViewContributor
import com.intellij.execution.services.ServiceViewLazyContributor
import com.intellij.execution.services.SimpleServiceViewDescriptor
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.AspireIcons
import com.jetbrains.aspire.worker.AspireWorkerManager

class AspireMainServiceViewContributor : ServiceViewContributor<AspireWorker>, ServiceViewLazyContributor {
    override fun getViewDescriptor(project: Project) =
        SimpleServiceViewDescriptor("Aspire", AspireIcons.Service)

    override fun getServices(project: Project): List<AspireWorker> {
        val worker = AspireWorkerManager.getInstance(project).aspireWorker
        return if (worker.hasAspireHosts) listOf(worker) else emptyList()
    }

    override fun getServiceDescriptor(project: Project, service: AspireWorker) =
        service.getViewDescriptor(project)
}