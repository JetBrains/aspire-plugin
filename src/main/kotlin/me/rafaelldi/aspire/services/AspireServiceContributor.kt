package me.rafaelldi.aspire.services

import com.intellij.execution.services.ServiceViewContributor
import com.intellij.execution.services.SimpleServiceViewDescriptor
import com.intellij.openapi.project.Project
import me.rafaelldi.aspire.AspireIcons

class AspireServiceContributor : ServiceViewContributor<AspireHostServiceContributor> {
    override fun getViewDescriptor(project: Project) =
        SimpleServiceViewDescriptor("Aspire", AspireIcons.Service)

    override fun getServices(project: Project) =
        AspireServiceManager.getInstance(project)
            .getHosts()
            .map { AspireHostServiceContributor(it) }
            .toMutableList()

    override fun getServiceDescriptor(project: Project, host: AspireHostServiceContributor) =
        host.getViewDescriptor(project)
}