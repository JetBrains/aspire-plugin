package com.jetbrains.rider.aspire.dashboard

import com.intellij.execution.services.ServiceViewDescriptor
import com.intellij.ide.projectView.PresentationData
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.ui.BadgeIconSupplier
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.util.ui.JBUI
import com.jetbrains.rider.aspire.AspireIcons
import com.jetbrains.rider.aspire.util.ASPIRE_HOST
import java.awt.BorderLayout
import javax.swing.JPanel

class AspireHostServiceViewDescriptor(private val aspireHost: AspireHost) : ServiceViewDescriptor, DataProvider {
    private val controlActionsIds = listOf("Aspire.Host.Run", "Aspire.Host.Debug", "Aspire.Host.Stop")
    private val secondaryActionsIds = listOf("Aspire.Host.Manifest", "Aspire.Host.Dashboard", "Aspire.Host.ResourceGraph")
    private val otherActionsIds = listOf("Aspire.Settings", "Aspire.Help")

    private val toolbarActions = DefaultActionGroup()

    init {
        val actionManager = ActionManager.getInstance()
        fun addAllActionsFromGroup(actionIds: List<String>) {
            for (actionId in actionIds) {
                val action = actionManager.getAction(actionId)
                if (action != null) {
                    toolbarActions.add(action)
                }
            }
        }

        addAllActionsFromGroup(controlActionsIds)
        toolbarActions.add(Separator())
        addAllActionsFromGroup(secondaryActionsIds)
        toolbarActions.add(Separator())
        addAllActionsFromGroup(otherActionsIds)
    }

    override fun getPresentation() = PresentationData().apply {
        var icon = AspireIcons.Service
        if (aspireHost.isActive) {
            icon = BadgeIconSupplier(icon).liveIndicatorIcon
        }
        setIcon(icon)
        addText(aspireHost.displayName, SimpleTextAttributes.REGULAR_ATTRIBUTES)
    }

    override fun getContentComponent(): JPanel {
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

    override fun getToolbarActions() = toolbarActions

    override fun getDataProvider() = this

    override fun getData(dataId: String) =
        if (ASPIRE_HOST.`is`(dataId)) aspireHost
        else null
}