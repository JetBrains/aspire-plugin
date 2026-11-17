package com.jetbrains.aspire.services

import com.intellij.execution.services.ServiceViewDescriptor
import com.intellij.ide.projectView.PresentationData
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBTabbedPane
import com.jetbrains.aspire.actions.ASPIRE_RESOURCE_DATA
import com.jetbrains.aspire.services.components.ResourceConsolePanel
import com.jetbrains.aspire.services.components.ResourceDashboardPanel
import com.jetbrains.aspire.services.components.ResourceDashboardState
import com.jetbrains.aspire.settings.AspireSettings
import java.awt.BorderLayout
import javax.swing.JPanel

internal class AspireResourceServiceViewDescriptor(
    private val vm: AspireResourceViewModel
) : ServiceViewDescriptor, UiDataProvider {

    private val resourceActions = ActionManager.getInstance().getAction("Aspire.Resource") as ActionGroup

    private var renderedDashboardState: ResourceDashboardState = currentDashboardState()
    private val dashboardPanel = ResourceDashboardPanel(renderedDashboardState)

    private val tabs = JBTabbedPane().apply {
        addTab(
            AspireServicesBundle.message("service.tab.dashboard"),
            dashboardPanel
        )
        addTab(
            AspireServicesBundle.message("service.tab.console"),
            ResourceConsolePanel(vm.uiState.value.consoleComponent)
        )

        if (AspireSettings.getInstance().openConsoleView) {
            selectedIndex = 1
        }
    }

    private val panel = JPanel(BorderLayout()).apply {
        add(tabs, BorderLayout.CENTER)
    }

    override fun getPresentation() = PresentationData().apply {
        val resourceData = vm.uiState.value.resourceData
        val icon = getIcon(resourceData)
        setIcon(icon)
        addText(resourceData.displayName, SimpleTextAttributes.REGULAR_ATTRIBUTES)
    }

    // The Service View calls this on every event of the Aspire contributor while the node is selected,
    // so the dashboard is rebuilt only when the rendered data (or the settings affecting it) has changed.
    override fun getContentComponent(): JPanel {
        val dashboardState = currentDashboardState()
        if (dashboardState != renderedDashboardState) {
            renderedDashboardState = dashboardState
            dashboardPanel.update(dashboardState)
        }
        return panel
    }

    private fun currentDashboardState(): ResourceDashboardState {
        val settings = AspireSettings.getInstance()
        return ResourceDashboardState(
            vm.uiState.value.resourceData,
            settings.showSensitiveProperties,
            settings.showEnvironmentVariables
        )
    }

    override fun getPopupActions() = resourceActions

    override fun uiDataSnapshot(sink: DataSink) {
        sink[ASPIRE_RESOURCE_DATA] = vm.resourceData.value
    }
}
