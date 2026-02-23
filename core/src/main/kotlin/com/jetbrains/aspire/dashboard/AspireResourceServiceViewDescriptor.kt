package com.jetbrains.aspire.dashboard

import com.intellij.execution.services.ServiceViewDescriptor
import com.intellij.ide.projectView.PresentationData
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBTabbedPane
import com.jetbrains.aspire.AspireCoreBundle
import com.jetbrains.aspire.dashboard.components.ResourceConsolePanel
import com.jetbrains.aspire.dashboard.components.ResourceDashboardPanel
import com.jetbrains.aspire.settings.AspireSettings
import com.jetbrains.aspire.util.ASPIRE_RESOURCE
import com.jetbrains.aspire.util.getIcon
import java.awt.BorderLayout
import javax.swing.JPanel

class AspireResourceServiceViewDescriptor(
    private val vm: AspireResourceViewModel
) : ServiceViewDescriptor, UiDataProvider {

    private val resourceActions = ActionManager.getInstance().getAction("Aspire.Resource") as ActionGroup

    private val tabs = JBTabbedPane().apply {
        addTab(
            AspireCoreBundle.message("service.tab.dashboard"),
            ResourceDashboardPanel(vm.uiState.value.resourceData)
        )
        addTab(
            AspireCoreBundle.message("service.tab.console"),
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

    override fun getContentComponent(): JPanel {
        val resourceData = vm.uiState.value.resourceData
        tabs.setComponentAt(0, ResourceDashboardPanel(resourceData))
        return panel
    }

    override fun getPopupActions() = resourceActions

    override fun uiDataSnapshot(sink: DataSink) {
        sink[ASPIRE_RESOURCE] = vm
    }
}
