@file:Suppress("UnstableApiUsage")

package com.jetbrains.rider.aspire.services

import com.intellij.execution.services.ServiceViewDescriptor
import com.intellij.execution.services.ServiceViewProvidingContributor
import com.intellij.ide.projectView.PresentationData
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.project.Project
import com.intellij.ui.BadgeIconSupplier
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.util.ui.JBUI
import com.jetbrains.rider.aspire.AspireIcons
import com.jetbrains.rider.aspire.util.ASPIRE_HOST_PATH
import java.awt.BorderLayout
import javax.swing.JPanel

class AspireHostServiceViewContributor(
    private val aspireHost: AspireHost
) : ServiceViewProvidingContributor<AspireResourceServiceViewContributor, AspireHost> {
    private val descriptor = AspireHostServiceViewDescriptor()

    override fun getViewDescriptor(project: Project): ServiceViewDescriptor {
        return descriptor
    }

    override fun asService() = aspireHost

    override fun getServices(project: Project) = aspireHost.getResources().map { it.serviceViewContributor }

    override fun getServiceDescriptor(
        project: Project,
        service: AspireResourceServiceViewContributor
    ) = service.getViewDescriptor(project)

    private fun getPanel(): JPanel {
        val console = aspireHost.consoleView
        val panel = if (console == null) {
            JBPanelWithEmptyText()
        } else {
            JPanel(BorderLayout()).apply {
                border = JBUI.Borders.empty()
                add(console.component)
            }
        }

        return panel
    }

    private inner class AspireHostServiceViewDescriptor : ServiceViewDescriptor, DataProvider {
        private val toolbarActions = DefaultActionGroup(
            ActionManager.getInstance().getAction("Aspire.Host.Run"),
            ActionManager.getInstance().getAction("Aspire.Host.Debug"),
            ActionManager.getInstance().getAction("Aspire.Host.Stop"),
            Separator(),
            ActionManager.getInstance().getAction("Aspire.Manifest"),
            ActionManager.getInstance().getAction("Aspire.Dashboard"),
            Separator(),
            ActionManager.getInstance().getAction("Aspire.Settings"),
            ActionManager.getInstance().getAction("Aspire.Help")
        )

        override fun getPresentation() = PresentationData().apply {
            var icon = AspireIcons.Service
            if (aspireHost.isActive) {
                icon = BadgeIconSupplier(icon).liveIndicatorIcon
            }
            setIcon(icon)
            addText(aspireHost.displayName, SimpleTextAttributes.REGULAR_ATTRIBUTES)
        }

        override fun getContentComponent() = getPanel()

        override fun getToolbarActions() = toolbarActions

        override fun getDataProvider() = this

        override fun getData(dataId: String) =
            if (ASPIRE_HOST_PATH.`is`(dataId)) aspireHost.hostProjectPath
            else null
    }
}