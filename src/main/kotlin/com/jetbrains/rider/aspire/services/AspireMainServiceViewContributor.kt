package com.jetbrains.rider.aspire.services

import com.intellij.execution.services.ServiceViewContributor
import com.intellij.execution.services.ServiceViewLazyContributor
import com.intellij.execution.services.SimpleServiceViewDescriptor
import com.intellij.openapi.project.Project
import com.jetbrains.rider.aspire.AspireIcons
import com.jetbrains.rider.aspire.sessionHost.SessionHostManager

class AspireMainServiceViewContributor : ServiceViewContributor<SessionHost>,
    ServiceViewLazyContributor {
    override fun getViewDescriptor(project: Project) =
        SimpleServiceViewDescriptor(".NET Aspire", AspireIcons.Service)

    override fun getServices(project: Project) =
        listOf(SessionHostManager.getInstance(project).sessionHost)

    override fun getServiceDescriptor(project: Project, service: SessionHost) =
        service.getViewDescriptor(project)
}