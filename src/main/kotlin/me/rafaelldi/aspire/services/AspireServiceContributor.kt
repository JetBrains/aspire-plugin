package me.rafaelldi.aspire.services

import com.intellij.execution.services.ServiceViewContributor
import com.intellij.execution.services.SimpleServiceViewDescriptor
import com.intellij.openapi.project.Project
import me.rafaelldi.aspire.AspireIcons
import me.rafaelldi.aspire.sessionHost.AspireSessionHostManager
import me.rafaelldi.aspire.settings.AspireSettings

class AspireServiceContributor : ServiceViewContributor<AspireSessionHostServiceContributor> {
    override fun getViewDescriptor(project: Project) =
        SimpleServiceViewDescriptor("Aspire", AspireIcons.Service)

    override fun getServices(project: Project): MutableList<AspireSessionHostServiceContributor> {
        val showServices = AspireSettings.getInstance().showServices
        return if (showServices)
            AspireSessionHostManager.getInstance(project)
                .getSessionHosts()
                .toMutableList()
        else
            mutableListOf()
    }

    override fun getServiceDescriptor(project: Project, host: AspireSessionHostServiceContributor) =
        host.getViewDescriptor(project)
}