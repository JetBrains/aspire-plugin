package me.rafaelldi.aspire.services

import com.intellij.execution.services.ServiceViewContributor
import com.intellij.execution.services.SimpleServiceViewDescriptor
import com.intellij.openapi.project.Project
import me.rafaelldi.aspire.AspireIcons
import me.rafaelldi.aspire.settings.AspireSettings

class AspireServiceContributor : ServiceViewContributor<AspireHostServiceContributor> {
    override fun getViewDescriptor(project: Project) =
        SimpleServiceViewDescriptor("Aspire", AspireIcons.Service)

    override fun getServices(project: Project): MutableList<AspireHostServiceContributor> {
        val showServices = AspireSettings.getInstance().showServices
        return if (showServices)
            AspireServiceManager.getInstance(project)
                .getHosts()
                .map { AspireHostServiceContributor(it) }
                .toMutableList()
        else
            mutableListOf()
    }

    override fun getServiceDescriptor(project: Project, host: AspireHostServiceContributor) =
        host.getViewDescriptor(project)
}