package com.jetbrains.rider.aspire.services.components

import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import com.jetbrains.rider.aspire.services.AspireResource

class ResourceConsolePanel(resourceService: AspireResource) : BorderLayoutPanel() {
    init {
        border = JBUI.Borders.empty()
        add(resourceService.logConsole.component)
    }
}