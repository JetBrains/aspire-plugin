package me.rafaelldi.aspire.services

import com.intellij.execution.services.ServiceViewDescriptor
import com.intellij.execution.services.ServiceViewProvidingContributor
import com.intellij.execution.services.SimpleServiceViewDescriptor
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.project.Project
import me.rafaelldi.aspire.AspireIcons
import me.rafaelldi.aspire.actions.OpenAspireDashboardAction
import me.rafaelldi.aspire.sessionHost.AspireSessionHostManager

class AspireHostServiceContributor(private val hostData: SessionHostServiceData) :
    ServiceViewProvidingContributor<SessionServiceData, SessionHostServiceData> {

    override fun getViewDescriptor(project: Project): ServiceViewDescriptor =
        object : SimpleServiceViewDescriptor(hostData.hostName, AspireIcons.Service) {
            private val toolbarActions = DefaultActionGroup(
                OpenAspireDashboardAction(hostData.dashboardUrl),
                Separator(),
                ActionManager.getInstance().getAction("Aspire.Settings"),
                ActionManager.getInstance().getAction("Aspire.Help")
            )

            override fun getToolbarActions() = toolbarActions
        }

    override fun asService() = hostData

    override fun getServices(project: Project) =
        AspireSessionHostManager.getInstance(project)
            .getSessions(hostData.id)
            .toMutableList()

    override fun getServiceDescriptor(
        project: Project,
        service: SessionServiceData
    ) = SessionServiceViewDescriptor(service)
}