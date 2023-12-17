package me.rafaelldi.aspire.services

import com.intellij.execution.services.ServiceViewContributor
import com.intellij.execution.services.ServiceViewDescriptor
import com.intellij.execution.services.SimpleServiceViewDescriptor
import com.intellij.openapi.project.Project
import me.rafaelldi.aspire.AspireIcons
import me.rafaelldi.aspire.sessionHost.AspireSessionHostConfig

class AspireHostServiceContributor(private val config: AspireSessionHostConfig) : ServiceViewContributor<Unit> {
    override fun getViewDescriptor(project: Project): ServiceViewDescriptor =
        SimpleServiceViewDescriptor(config.projectName, AspireIcons.Service)

    override fun getServices(project: Project): MutableList<Unit> {
        return mutableListOf()
    }

    override fun getServiceDescriptor(project: Project, service: Unit): ServiceViewDescriptor {
        return SimpleServiceViewDescriptor("Service", AspireIcons.Service)
    }
}