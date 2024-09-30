package com.jetbrains.rider.aspire.services

import com.intellij.execution.services.ServiceViewContributor
import com.intellij.execution.services.ServiceViewLazyContributor
import com.intellij.execution.services.SimpleServiceViewDescriptor
import com.intellij.openapi.project.Project
import com.jetbrains.rider.aspire.AspireIcons

class AspireMainServiceViewContributor : ServiceViewContributor<AspireHostServiceViewContributor>,
    ServiceViewLazyContributor {
    override fun getViewDescriptor(project: Project) =
        SimpleServiceViewDescriptor("Aspire", AspireIcons.Service)

    override fun getServices(project: Project) =
        AspireHostManager.getInstance(project).getAspireHosts().map { it.serviceViewContributor }

    override fun getServiceDescriptor(
        project: Project,
        service: AspireHostServiceViewContributor
    ) = service.getViewDescriptor(project)
}