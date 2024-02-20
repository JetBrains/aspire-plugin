package me.rafaelldi.aspire.services

import com.intellij.execution.services.ServiceViewDescriptor
import com.intellij.execution.services.ServiceViewProvidingContributor
import com.intellij.execution.services.SimpleServiceViewDescriptor
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.project.Project
import me.rafaelldi.aspire.AspireIcons
import me.rafaelldi.aspire.sessionHost.AspireSessionHostManager
import me.rafaelldi.aspire.util.SESSION_HOST_ID

class AspireHostServiceContributor(val hostData: AspireHostServiceData) :
    ServiceViewProvidingContributor<AspireResourceServiceData, AspireHostServiceData> {

    override fun getViewDescriptor(project: Project): ServiceViewDescriptor =
        object : SimpleServiceViewDescriptor(hostData.name, AspireIcons.Service), DataProvider {
            private val toolbarActions = DefaultActionGroup(
                ActionManager.getInstance().getAction("Aspire.Dashboard"),
                ActionManager.getInstance().getAction("Aspire.Manifest"),
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

    override fun asService() = hostData

    override fun getServices(project: Project) =
        AspireSessionHostManager.getInstance(project)
            .getResources(hostData.id)
            .toMutableList()

    override fun getServiceDescriptor(
        project: Project,
        service: AspireResourceServiceData
    ) = AspireResourceServiceViewDescriptor(service)
}