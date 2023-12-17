package me.rafaelldi.aspire.services

import com.intellij.execution.services.ServiceViewDescriptor
import com.intellij.execution.services.ServiceViewProvidingContributor
import com.intellij.openapi.project.Project

class AspireProjectViewContributor: ServiceViewProvidingContributor<String, String> {
    override fun getViewDescriptor(project: Project): ServiceViewDescriptor {
        TODO("Not yet implemented")
    }

    override fun getServices(project: Project): MutableList<String> {
        return mutableListOf()
    }

    override fun asService(): String {
        TODO("Not yet implemented")
    }

    override fun getServiceDescriptor(project: Project, service: String): ServiceViewDescriptor {
        TODO("Not yet implemented")
    }

}