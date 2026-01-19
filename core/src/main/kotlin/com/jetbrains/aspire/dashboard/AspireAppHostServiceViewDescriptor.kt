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

    private val toolbarActions = DefaultActionGroup(
        ActionManager.getInstance().getAction("Aspire.Host.Run"),
        ActionManager.getInstance().getAction("Aspire.Host.Debug"),
        ActionManager.getInstance().getAction("Aspire.Host.Stop"),
        Separator(),
        ActionManager.getInstance().getAction("Aspire.Host.Manifest"),
        ActionManager.getInstance().getAction("Aspire.Host.Dashboard"),
        ActionManager.getInstance().getAction("Aspire.Host.ResourceGraph"),
        Separator(),
        ActionManager.getInstance().getAction("Aspire.Settings"),
        ActionManager.getInstance().getAction("Aspire.Help")
    )

    override fun getPresentation() = PresentationData().apply {
        var icon = AspireIcons.Service
        if (vm.isActive) {
            icon = BadgeIconSupplier(icon).liveIndicatorIcon
        }
        setIcon(icon)
        addText(vm.displayName, SimpleTextAttributes.REGULAR_ATTRIBUTES)
    }

    override fun getContentComponent(): JPanel {
        val console = vm.consoleView
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

    override fun getToolbarActions() = toolbarActions

    override fun uiDataSnapshot(sink: DataSink) {
        sink[ASPIRE_APP_HOST] = vm
    }
}