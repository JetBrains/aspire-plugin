@file:Suppress("UnstableApiUsage")

package com.jetbrains.rider.aspire.services

import com.intellij.execution.services.ServiceViewDescriptor
import com.intellij.ide.projectView.PresentationData
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBTabbedPane
import com.jetbrains.rider.aspire.AspireBundle
import com.jetbrains.rider.aspire.services.components.ResourceConsolePanel
import com.jetbrains.rider.aspire.services.components.ResourceDashboardPanel
import com.jetbrains.rider.aspire.util.*
import java.awt.BorderLayout
import javax.swing.JPanel

class AspireResourceServiceViewDescriptor(
    private val resourceService: AspireResourceService
) : ServiceViewDescriptor, DataProvider {

    private val toolbarActions = DefaultActionGroup(
        ActionManager.getInstance().getAction("Aspire.Resource.Stop")
    )

    private val mainPanel by lazy {
        val tabs = JBTabbedPane()
        tabs.addTab(AspireBundle.getMessage("service.tab.dashboard"), ResourceDashboardPanel(resourceService))
        tabs.addTab(AspireBundle.getMessage("service.tab.console"), ResourceConsolePanel(resourceService))

        JPanel(BorderLayout()).apply {
            add(tabs, BorderLayout.CENTER)
        }
    }

    override fun getPresentation() = PresentationData().apply {
        val icon = getIcon(resourceService.type, resourceService.state)
        setIcon(icon)
        addText(resourceService.displayName, SimpleTextAttributes.REGULAR_ATTRIBUTES)
    }

    override fun getContentComponent() = mainPanel

    override fun getToolbarActions() = toolbarActions

    override fun getDataProvider() = this

    override fun getData(dataId: String) =
        if (ASPIRE_RESOURCE_UID.`is`(dataId)) resourceService.uid
        else if (ASPIRE_RESOURCE_SERVICE_INSTANCE_ID.`is`(dataId)) resourceService.serviceInstanceId
        else if (ASPIRE_RESOURCE_TYPE.`is`(dataId)) resourceService.type
        else if (ASPIRE_RESOURCE_STATE.`is`(dataId)) resourceService.state
        else null
}