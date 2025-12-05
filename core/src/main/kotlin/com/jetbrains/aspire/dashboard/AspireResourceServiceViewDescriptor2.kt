package com.jetbrains.aspire.dashboard

import com.intellij.execution.services.ServiceViewDescriptor
import com.intellij.ide.projectView.PresentationData
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBTabbedPane
import com.jetbrains.aspire.util.ASPIRE_RESOURCE2
import java.awt.BorderLayout
import javax.swing.JPanel

class AspireResourceServiceViewDescriptor2(
    private val vm: AspireResourceViewModel
) : ServiceViewDescriptor, DataProvider {

    private val toolbarActions = ActionManager.getInstance().getAction("Aspire.Resource") as ActionGroup

    private val tabs = JBTabbedPane().apply {

    }

    private val panel = JPanel(BorderLayout()).apply {
        add(tabs, BorderLayout.CENTER)
    }

    override fun getPresentation() = PresentationData().apply {
        addText("Resource", SimpleTextAttributes.REGULAR_ATTRIBUTES)
    }

    override fun getContentComponent(): JPanel {
        return panel
    }

    override fun getToolbarActions() = toolbarActions

    override fun getDataProvider() = this

    override fun getData(dataId: String) =
        if (ASPIRE_RESOURCE2.`is`(dataId)) vm
        else null
}