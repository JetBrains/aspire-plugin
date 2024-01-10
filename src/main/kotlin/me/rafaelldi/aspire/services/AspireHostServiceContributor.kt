package me.rafaelldi.aspire.services

import com.intellij.execution.services.ServiceViewProvidingContributor
import com.intellij.execution.services.SimpleServiceViewDescriptor
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import me.rafaelldi.aspire.AspireIcons
import me.rafaelldi.aspire.actions.OpenAspireDashboardAction
import me.rafaelldi.aspire.sessionHost.AspireSessionHostManager
import me.rafaelldi.aspire.util.SESSION_HOST_ID

class AspireHostServiceContributor(private val hostData: SessionHostServiceData) :
    ServiceViewProvidingContributor<SessionServiceData, SessionHostServiceData> {

    private val viewDescriptor by lazy {
        object : SimpleServiceViewDescriptor(hostData.hostName, AspireIcons.Service), DataProvider {
            private val toolbarActions = DefaultActionGroup(
                OpenAspireDashboardAction(hostData.dashboardUrl),
                ActionManager.getInstance().getAction("Aspire.Diagram"),
                Separator(),
                ActionManager.getInstance().getAction("Aspire.Settings"),
                ActionManager.getInstance().getAction("Aspire.Help")
            )

            override fun getToolbarActions() = toolbarActions

            override fun getDataProvider() = this

            override fun getData(dataId: String) =
                if (SESSION_HOST_ID.`is`(dataId)) hostData.id
                else null
        }
    }

    override fun getViewDescriptor(project: Project) = viewDescriptor

    override fun asService() = hostData

    override fun getServices(project: Project) =
        AspireSessionHostManager.getInstance(project)
            .getSessions(hostData.id)
            .toMutableList()

    override fun getServiceDescriptor(project: Project, service: SessionServiceData) =
        service.getViewDescriptor()
}