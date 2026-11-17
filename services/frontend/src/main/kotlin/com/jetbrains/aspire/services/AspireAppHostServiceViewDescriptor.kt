package com.jetbrains.aspire.services

import com.intellij.execution.services.ServiceViewDescriptor
import com.intellij.ide.projectView.PresentationData
import com.intellij.openapi.actionSystem.*
import com.intellij.ui.BadgeIconSupplier
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.util.ui.JBUI
import com.jetbrains.aspire.actions.ASPIRE_APP_HOST_DASHBOARD_URL
import com.jetbrains.aspire.actions.ASPIRE_APP_HOST_DATA
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

internal class AspireAppHostServiceViewDescriptor(
    private val vm: AspireAppHostViewModel
) : ServiceViewDescriptor, UiDataProvider {

    private val appHostActions = ActionManager.getInstance().getAction("Aspire.Host.Tollbar") as ActionGroup

    override fun getPresentation() = PresentationData().apply {
        var icon = AspireServicesIcons.Service
        if (vm.uiState.value is AppHostUiState.Active) {
            icon = BadgeIconSupplier(icon).liveIndicatorIcon
        }
        setIcon(icon)
        addText(vm.displayName, SimpleTextAttributes.REGULAR_ATTRIBUTES)
    }

    private val emptyPanel = JBPanelWithEmptyText()

    private val consolePanel = JPanel(BorderLayout()).apply {
        border = JBUI.Borders.empty()
    }

    // The Service View calls this on every event of the Aspire contributor while the node is selected,
    // so the same component instances are returned to avoid re-parenting the console each time.
    override fun getContentComponent(): JPanel {
        return when (val state = vm.uiState.value) {
            is AppHostUiState.Initial -> emptyPanel
            is AppHostUiState.Active -> consolePanelWith(state.consoleComponent)
            is AppHostUiState.Inactive -> consolePanelWith(state.consoleComponent)
        }
    }

    private fun consolePanelWith(consoleComponent: JComponent): JPanel {
        if (consoleComponent.parent !== consolePanel) {
            consolePanel.removeAll()
            consolePanel.add(consoleComponent, BorderLayout.CENTER)
        }
        return consolePanel
    }

    override fun getToolbarActions() = appHostActions

    override fun uiDataSnapshot(sink: DataSink) {
        sink[ASPIRE_APP_HOST_DATA] = vm.appHostData.value
        sink[ASPIRE_APP_HOST_DASHBOARD_URL] = vm.aspireDashboardUrl.value
    }
}
