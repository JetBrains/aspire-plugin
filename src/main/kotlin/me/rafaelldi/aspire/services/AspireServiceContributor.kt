package me.rafaelldi.aspire.services

import com.intellij.execution.services.ServiceViewContributor
import com.intellij.execution.services.SimpleServiceViewDescriptor
import com.intellij.openapi.project.Project
import me.rafaelldi.aspire.AspireIcons
import me.rafaelldi.aspire.sessionHost.AspireSessionHostManager

class AspireServiceContributor : ServiceViewContributor<AspireSessionHostServiceContributor> {
    override fun getViewDescriptor(project: Project) =
        SimpleServiceViewDescriptor("Aspire", AspireIcons.Service)

    override fun getServices(project: Project) =
        AspireSessionHostManager.getInstance(project)
            .getSessionHosts()
            .toMutableList()

    override fun getServiceDescriptor(project: Project, host: AspireSessionHostServiceContributor) =
        host.getViewDescriptor(project)
}