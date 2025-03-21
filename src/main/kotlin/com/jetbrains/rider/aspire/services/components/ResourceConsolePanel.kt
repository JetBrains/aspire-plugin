package com.jetbrains.rider.aspire.services.components

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import com.jetbrains.rider.aspire.services.AspireResource
import java.awt.BorderLayout

class ResourceConsolePanel(resourceService: AspireResource) : BorderLayoutPanel() {
    init {
        border = JBUI.Borders.empty()
        add(resourceService.logConsoleComponent)

        val actionManager = ActionManager.getInstance()
        val toolbarActions = actionManager.getAction("Aspire.Resource") as ActionGroup
        val toolbar = actionManager.createActionToolbar(ActionPlaces.TOOLBAR, toolbarActions, true)
        toolbar.targetComponent = this
        add(toolbar.component, BorderLayout.NORTH)
    }
}