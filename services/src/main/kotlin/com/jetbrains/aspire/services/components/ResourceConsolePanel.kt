package com.jetbrains.aspire.services.components

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.BorderLayout
import javax.swing.JComponent

internal class ResourceConsolePanel(consoleComponent: JComponent) : BorderLayoutPanel() {
    init {
        border = JBUI.Borders.empty()
        add(consoleComponent)

        val actionManager = ActionManager.getInstance()
        val toolbarActions = actionManager.getAction("Aspire.Resource") as ActionGroup
        val toolbar = actionManager.createActionToolbar(ActionPlaces.TOOLBAR, toolbarActions, true).apply {
            targetComponent = this@ResourceConsolePanel
            component.border = JBUI.Borders.compound(
                IdeBorderFactory.createBorder(SideBorder.BOTTOM),
                component.border
            )
        }
        add(toolbar.component, BorderLayout.NORTH)
    }
}
