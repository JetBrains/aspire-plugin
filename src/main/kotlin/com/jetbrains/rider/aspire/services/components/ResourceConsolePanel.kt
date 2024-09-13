package com.jetbrains.rider.aspire.services.components

import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import com.jetbrains.rider.aspire.services.AspireResourceService

class ResourceConsolePanel(resourceService: AspireResourceService) : BorderLayoutPanel() {
    init {
        border = JBUI.Borders.empty()
        add(resourceService.consoleView.component)
    }
}