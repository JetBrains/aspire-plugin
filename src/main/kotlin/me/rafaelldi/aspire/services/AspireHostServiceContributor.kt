package me.rafaelldi.aspire.services

import com.intellij.execution.services.ServiceViewContributor
import com.intellij.execution.services.ServiceViewDescriptor
import com.intellij.execution.services.SimpleServiceViewDescriptor
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.project.Project
import me.rafaelldi.aspire.AspireIcons
import me.rafaelldi.aspire.actions.OpenAspireDashboardAction

class AspireHostServiceContributor(private val data: AspireServiceManager.HostServiceData) :
    ServiceViewContributor<Unit> {
    override fun getViewDescriptor(project: Project): ServiceViewDescriptor =
        object : SimpleServiceViewDescriptor(data.hostName, AspireIcons.Service) {
            private val toolbarActions = DefaultActionGroup(
                OpenAspireDashboardAction(data.dashboardUrl),
                Separator(),
                ActionManager.getInstance().getAction("Aspire.Settings"),
                ActionManager.getInstance().getAction("Aspire.Help")
            )

            override fun getToolbarActions() = toolbarActions
        }

    override fun getServices(project: Project): MutableList<Unit> {
        return mutableListOf()
    }

    override fun getServiceDescriptor(project: Project, service: Unit): ServiceViewDescriptor {
        return SimpleServiceViewDescriptor("Service", AspireIcons.Service)
    }
}