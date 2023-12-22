package me.rafaelldi.aspire.services.components

import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SideBorder
import com.intellij.util.ui.components.BorderLayoutPanel
import me.rafaelldi.aspire.services.SessionServiceData

class EnvironmentVariablePanel(sessionData: SessionServiceData) : BorderLayoutPanel() {
    init {
        val table = EnvironmentVariableTable(sessionData.sessionModel.envs ?: emptyArray())
        add(ScrollPaneFactory.createScrollPane(table, SideBorder.NONE))
    }
}