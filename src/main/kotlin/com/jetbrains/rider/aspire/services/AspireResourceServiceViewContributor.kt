package com.jetbrains.rider.aspire.services

import com.intellij.execution.services.ServiceViewDescriptor
import com.intellij.execution.services.ServiceViewProvidingContributor
import com.intellij.ide.projectView.PresentationData
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBTabbedPane
import com.jetbrains.rider.aspire.AspireBundle
import com.jetbrains.rider.aspire.services.components.ResourceConsolePanel
import com.jetbrains.rider.aspire.services.components.ResourceDashboardPanel
import com.jetbrains.rider.aspire.util.*
import java.awt.BorderLayout
import javax.swing.JPanel

class AspireResourceServiceViewContributor(
    private val aspireResource: AspireResource
) : ServiceViewProvidingContributor<AspireResourceServiceViewContributor, AspireResource> {
    private val descriptor = AspireResourceServiceViewDescriptor()

    override fun getViewDescriptor(project: Project): ServiceViewDescriptor {
        return descriptor
    }

    override fun asService() = aspireResource

    override fun getServices(project: Project): List<AspireResourceServiceViewContributor?> {
        return emptyList()
    }

    override fun getServiceDescriptor(
        project: Project,
        service: AspireResourceServiceViewContributor
    ) = service.getViewDescriptor(project)

    private fun getPanel(): JPanel {
        val tabs = JBTabbedPane()
        tabs.addTab(AspireBundle.getMessage("service.tab.dashboard"), ResourceDashboardPanel(aspireResource))
        tabs.addTab(AspireBundle.getMessage("service.tab.console"), ResourceConsolePanel(aspireResource))

        val panel = JPanel(BorderLayout()).apply {
            add(tabs, BorderLayout.CENTER)
        }

        return panel
    }

    private inner class AspireResourceServiceViewDescriptor : ServiceViewDescriptor, DataProvider {
        private val toolbarActions = DefaultActionGroup(
            ActionManager.getInstance().getAction("Aspire.Resource.Stop")
        )

        override fun getPresentation() = PresentationData().apply {
            val icon = getIcon(aspireResource.type, aspireResource.state)
            setIcon(icon)
            addText(aspireResource.displayName, SimpleTextAttributes.REGULAR_ATTRIBUTES)
        }

        override fun getContentComponent() = getPanel()

        override fun getToolbarActions() = toolbarActions

        override fun getDataProvider() = this

        override fun getData(dataId: String) =
            if (ASPIRE_RESOURCE_UID.`is`(dataId)) aspireResource.uid
            else if (ASPIRE_RESOURCE_SERVICE_INSTANCE_ID.`is`(dataId)) aspireResource.serviceInstanceId
            else if (ASPIRE_RESOURCE_TYPE.`is`(dataId)) aspireResource.type
            else if (ASPIRE_RESOURCE_STATE.`is`(dataId)) aspireResource.state
            else null
    }
}