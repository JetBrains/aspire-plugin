package com.jetbrains.aspire.dashboard

import com.intellij.execution.services.ServiceViewDescriptor
import com.intellij.ide.projectView.PresentationData
import com.intellij.openapi.actionSystem.*
import com.intellij.ui.BadgeIconSupplier
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.util.ui.JBUI
import com.jetbrains.aspire.AspireIcons
import com.jetbrains.aspire.util.ASPIRE_APP_HOST
import java.awt.BorderLayout
import javax.swing.JPanel

class AspireAppHostServiceViewDescriptor(
    private val vm: AspireAppHostViewModel
) : ServiceViewDescriptor, UiDataProvider {

    private val appHostActions =  ActionManager.getInstance().getAction("Aspire.Host.Tollbar") as ActionGroup

    override fun getPresentation() = PresentationData().apply {
        var icon = AspireIcons.Service
        if (vm.uiState.value is AppHostUiState.Active) {
            icon = BadgeIconSupplier(icon).liveIndicatorIcon
        }
        setIcon(icon)
        addText(vm.displayName, SimpleTextAttributes.REGULAR_ATTRIBUTES)
    }

    override fun getContentComponent(): JPanel {
        val state = vm.uiState.value
        return if (state is AppHostUiState.Active) {
            JPanel(BorderLayout()).apply {
                border = JBUI.Borders.empty()
                add(state.consoleView.component)
            }
        } else {
            JBPanelWithEmptyText()
        }
    }

    override fun getToolbarActions() = appHostActions

    override fun uiDataSnapshot(sink: DataSink) {
        sink[ASPIRE_APP_HOST] = vm
    }
}