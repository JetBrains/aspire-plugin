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
import com.jetbrains.rider.aspire.util.ASPIRE_RESOURCE
import com.jetbrains.rider.aspire.util.getIcon
import java.awt.BorderLayout
import javax.swing.JPanel

class AspireResourceServiceViewDescriptor(private val aspireResource: AspireResource) : ServiceViewDescriptor, DataProvider {
    private val toolbarActions = DefaultActionGroup(
        ActionManager.getInstance().getAction("Aspire.Resource.Start"),
        ActionManager.getInstance().getAction("Aspire.Resource.Restart"),
        ActionManager.getInstance().getAction("Aspire.Resource.Stop"),
        ActionManager.getInstance().getAction("Aspire.Resource.Attach")
    )

    override fun getPresentation() = PresentationData().apply {
        val icon = getIcon(aspireResource)
        setIcon(icon)
        addText(aspireResource.displayName, SimpleTextAttributes.REGULAR_ATTRIBUTES)
    }

    override fun getContentComponent(): JPanel {
        val tabs = JBTabbedPane()
        tabs.addTab(AspireBundle.message("service.tab.dashboard"), ResourceDashboardPanel(aspireResource))
        tabs.addTab(AspireBundle.message("service.tab.console"), ResourceConsolePanel(aspireResource))

        val panel = JPanel(BorderLayout()).apply {
            add(tabs, BorderLayout.CENTER)
        }

        return panel
    }

    override fun getToolbarActions() = toolbarActions

    override fun getDataProvider() = this

    override fun getData(dataId: String) =
        if (ASPIRE_RESOURCE.`is`(dataId)) aspireResource
        else null
}