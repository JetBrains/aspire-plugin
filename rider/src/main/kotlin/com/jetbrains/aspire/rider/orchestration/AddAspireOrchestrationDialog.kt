package com.jetbrains.aspire.rider.orchestration

import com.intellij.execution.multilaunch.design.components.IconCheckBoxList
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SeparatorComponent
import com.intellij.ui.SeparatorOrientation
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import com.jetbrains.aspire.rider.AspireRiderBundle
import com.jetbrains.rider.projectView.calculateIcon
import com.jetbrains.rider.projectView.workspace.ProjectModelEntity
import com.jetbrains.rider.ui.px
import java.awt.BorderLayout
import java.awt.event.ItemEvent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
import javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED

class AddAspireOrchestrationDialog(project: Project, projectEntities: List<ProjectModelEntity>) :
    DialogWrapper(project) {

    private val selectAll = JBCheckBox(AspireRiderBundle.message("dialog.add.orchestration.select.all")).apply {
        border = JBUI.Borders.empty(2, 1)
    }

    private val separator =
        SeparatorComponent(JBUI.CurrentTheme.List.buttonSeparatorColor(), SeparatorOrientation.HORIZONTAL).apply {
            setHGap(4)
        }

    private val selector = object : IconCheckBoxList<ProjectModelEntity>() {
        override fun getIcon(item: ProjectModelEntity?) = item?.calculateIcon(project)
        override fun getText(item: ProjectModelEntity?) = item?.name
    }.apply {
        border = JBUI.Borders.empty()
    }

    init {
        title = AspireRiderBundle.message("dialog.add.orchestration.title")

        projectEntities
            .sortedBy { it.name }
            .forEach { selector.addItem(it, it.name, false) }

        selectAll.addItemListener {
            if (it.stateChange == ItemEvent.SELECTED) {
                for (i in 0 until selector.itemsCount) {
                    selector.setItemSelected(selector.getItemAt(i), true)
                }
                selector.repaint()
            }
        }

        selector.setCheckBoxListListener { _, checked ->
            if (!checked) selectAll.isSelected = false
        }

        init()
    }

    override fun createCenterPanel() = BorderLayoutPanel().apply {
        val panel = JPanel(VerticalFlowLayout()).apply {
            add(selectAll)
            add(separator)
            add(selector)
        }

        val scrollPane = ScrollPaneFactory.createScrollPane(panel).apply {
            minimumSize = JBDimension(350.px, 200.px)
            preferredSize = JBDimension(350.px, 400.px)
            maximumSize = JBDimension(500.px, 600.px)
            verticalScrollBarPolicy = VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = HORIZONTAL_SCROLLBAR_AS_NEEDED
        }

        add(scrollPane, BorderLayout.CENTER)
    }

    fun getSelectedItems(): List<ProjectModelEntity> {
        return buildList {
            for (i in 0 until selector.itemsCount) {
                if (!selector.isItemSelected(i)) continue

                val projectEntity = selector.getItemAt(i) ?: continue
                add(projectEntity)
            }
        }
    }
}