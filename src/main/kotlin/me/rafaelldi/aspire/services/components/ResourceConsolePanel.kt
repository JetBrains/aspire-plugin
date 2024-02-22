package me.rafaelldi.aspire.services.components

import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import me.rafaelldi.aspire.services.AspireResourceServiceData

class ResourceConsolePanel(resourceData: AspireResourceServiceData) : BorderLayoutPanel() {
    init {
        border = JBUI.Borders.empty()
        add(resourceData.getConsole().component)
    }
}