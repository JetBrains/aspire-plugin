package com.jetbrains.aspire.services.components

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import com.jetbrains.aspire.services.AspireServicesBundle
import com.jetbrains.aspire.services.getIcon
import com.jetbrains.aspire.worker.ResourceState
import com.jetbrains.aspire.worker.ResourceType
import com.jetbrains.aspire.worker.AspireResourceData
import com.jetbrains.aspire.worker.AspirePath
import com.jetbrains.aspire.worker.AspireResourceProperty

/**
 * Everything [ResourceDashboardPanel] renders: the resource data and the display settings that affect it.
 */
internal data class ResourceDashboardState(
    val resourceData: AspireResourceData,
    val showSensitiveProperties: Boolean,
    val showEnvironmentVariables: Boolean,
)

internal class ResourceDashboardPanel(state: ResourceDashboardState) : BorderLayoutPanel() {
    private val toolbar = ActionManager.getInstance().let { actionManager ->
        val toolbarActions = actionManager.getAction("Aspire.Resource") as ActionGroup
        actionManager.createActionToolbar(ActionPlaces.TOOLBAR, toolbarActions, true).apply {
            targetComponent = this@ResourceDashboardPanel
        }
    }

    private val scrollPane = ScrollPaneFactory.createScrollPane(null, SideBorder.NONE)

    init {
        border = JBUI.Borders.empty(5, 10)
        add(scrollPane)
        update(state)
    }

    /**
     * Rebuilds the dashboard content for [state] in place, keeping this component,
     * its toolbar and the scroll position.
     */
    fun update(state: ResourceDashboardState) {
        val content = setUpPanel(state)

        val viewport = scrollPane.viewport
        val viewPosition = viewport.viewPosition
        scrollPane.setViewportView(content)
        scrollPane.validate()
        viewport.viewPosition = viewPosition
    }

    private fun setUpPanel(state: ResourceDashboardState): DialogPanel = panel {
        val resourceData = state.resourceData
        addHeader(resourceData)
        addProperties(resourceData, state.showSensitiveProperties)
        addEndpoints(resourceData)
        addVolumes(resourceData)
        addEnvironmentVariables(resourceData, state.showEnvironmentVariables)
    }

    private fun Panel.addHeader(data: AspireResourceData) {
        row {
            addIconAndTitle(data)
            addStateAndHealth(data)
            addActionToolbar()
        }
        separator()
    }

    private fun Row.addIconAndTitle(data: AspireResourceData) {
        val resourceIcon = getIcon(data)
        icon(resourceIcon)
            .gap(RightGap.SMALL)
        copyableLabel(data.displayName)
            .bold()
            .gap(RightGap.SMALL)

        if (data.type == ResourceType.Project) {
            data.projectPath?.value?.let {
                copyableLabel(it.fileName, color = UIUtil.FontColor.BRIGHTER)
                    .gap(RightGap.SMALL)
            }
        }

        if (data.type == ResourceType.Container) {
            data.containerImage?.value?.let {
                copyableLabel(it, color = UIUtil.FontColor.BRIGHTER)
                    .gap(RightGap.SMALL)
            }
        }

        if (data.type == ResourceType.Executable) {
            data.executablePath?.value?.let {
                copyableLabel(it.fileName, color = UIUtil.FontColor.BRIGHTER)
                    .gap(RightGap.SMALL)
            }
        }
    }

    private fun Row.addStateAndHealth(data: AspireResourceData) {
        val state = data.state
        if (state != null) {
            separator()
                .gap(RightGap.SMALL)

            val healthStatus = data.healthStatus
            val hasHealthStatus = state == ResourceState.Running && healthStatus != null

            val text = if (hasHealthStatus) {
                "${state.name} (${healthStatus.name})"
            } else {
                state.name
            }
            copyableLabel(text, color = UIUtil.FontColor.BRIGHTER)
                .gap(RightGap.SMALL)
        }
    }

    private fun Row.addActionToolbar() {
        cell(toolbar.component)
    }

    private fun Panel.addEndpoints(data: AspireResourceData) {
        if (data.urls.isNotEmpty()) {
            row {
                label(AspireServicesBundle.message("service.tab.dashboard.endpoints")).bold()
            }.bottomGap(BottomGap.SMALL)
            data.urls
                .sortedBy { it.sortOrder }
                .forEach { url ->
                    if (!url.isInternal) {
                        val endpointNameValue = url.endpointName
                        val endpointName =
                            if (url.displayName.isNotEmpty()) url.displayName
                            else if (!endpointNameValue.isNullOrEmpty()) endpointNameValue
                            else "-"
                        row(endpointName) {
                            link(url.fullUrl) {
                                BrowserUtil.browse(url.fullUrl)
                            }
                        }
                    }
                }
            separator()
        }
    }

    private fun Panel.addProperties(data: AspireResourceData, showSensitive: Boolean) {
        row {
            label(AspireServicesBundle.message("service.tab.dashboard.properties")).bold()
        }.bottomGap(BottomGap.SMALL)

        fun Panel.stringPropertyRow(label: String, property: AspireResourceProperty<String>?) =
            propertyRow(label, property, showSensitive) { it }

        fun Panel.intPropertyRow(label: String, property: AspireResourceProperty<Int>?) =
            propertyRow(label, property, showSensitive) { it.toString() }

        fun Panel.pathPropertyRow(label: String, property: AspireResourceProperty<AspirePath>?) =
            propertyRow(label, property, showSensitive) { it.value }

        with(data) {
            state?.let {
                row(AspireServicesBundle.message("service.tab.dashboard.properties.state")) { copyableLabel(it.name) }
            }
            healthStatus?.let {
                row(AspireServicesBundle.message("service.tab.dashboard.properties.health.status")) { copyableLabel(it.name) }
            }
            createdAt?.let {
                row(AspireServicesBundle.message("service.tab.dashboard.properties.creation.time")) { copyableLabel(it.toString()) }
            }
            startedAt?.let {
                row(AspireServicesBundle.message("service.tab.dashboard.properties.start.time")) { copyableLabel(it.toString()) }
            }
            stoppedAt?.let {
                row(AspireServicesBundle.message("service.tab.dashboard.properties.stop.time")) { copyableLabel(it.toString()) }
            }
            intPropertyRow(AspireServicesBundle.message("service.tab.dashboard.properties.pid"), data.pid)
            intPropertyRow(AspireServicesBundle.message("service.tab.dashboard.properties.exit.code"), data.exitCode)
            pathPropertyRow(AspireServicesBundle.message("service.tab.dashboard.properties.project"), projectPath)
            pathPropertyRow(AspireServicesBundle.message("service.tab.dashboard.properties.executable"), executablePath)
            pathPropertyRow(AspireServicesBundle.message("service.tab.dashboard.properties.working.dir"), executableWorkDir)
            stringPropertyRow(AspireServicesBundle.message("service.tab.dashboard.properties.args"), args)
            stringPropertyRow(AspireServicesBundle.message("service.tab.dashboard.properties.container.image"), containerImage)
            stringPropertyRow(AspireServicesBundle.message("service.tab.dashboard.properties.container.id"), containerId)
            stringPropertyRow(AspireServicesBundle.message("service.tab.dashboard.properties.container.ports"), containerPorts)
            stringPropertyRow(AspireServicesBundle.message("service.tab.dashboard.properties.container.command"), containerCommand)
            stringPropertyRow(AspireServicesBundle.message("service.tab.dashboard.properties.container.args"), containerArgs)
            stringPropertyRow(AspireServicesBundle.message("service.tab.dashboard.properties.connection.string"), connectionString)
            stringPropertyRow(AspireServicesBundle.message("service.tab.dashboard.properties.source"), source)
            stringPropertyRow(AspireServicesBundle.message("service.tab.dashboard.properties.value"), value)
            separator()
        }
    }

    private fun Panel.addVolumes(data: AspireResourceData) {
        if (data.volumes.isNotEmpty()) {
            row {
                label(AspireServicesBundle.message("service.tab.dashboard.volumes")).bold()
            }.bottomGap(BottomGap.SMALL)
            data.volumes
                .sortedBy { it.source }
                .forEach { volume ->
                    row {
                        copyableLabel("${volume.source} : ${volume.target}")
                            .gap(RightGap.SMALL)

                        copyableLabel(volume.mountType, color = UIUtil.FontColor.BRIGHTER)
                            .apply { if (volume.isReadOnly) gap(RightGap.SMALL) }

                        if (volume.isReadOnly) {
                            copyableLabel("(${AspireServicesBundle.message("service.tab.dashboard.volumes.readonly")})")
                        }
                    }
                }
            separator()
        }
    }

    private fun Panel.addEnvironmentVariables(data: AspireResourceData, showEnvironment: Boolean) {
        if (data.environment.isNotEmpty()) {
            row {
                label(AspireServicesBundle.message("service.tab.dashboard.environment")).bold()
            }.bottomGap(BottomGap.SMALL)
            data.environment.forEach { variable ->
                val valueText = if (showEnvironment) (variable.value ?: "-") else "*****"
                row {
                    copyableLabel("${variable.key} = $valueText")
                }
            }
        }
    }

    private fun <T> Panel.propertyRow(
        label: String,
        property: AspireResourceProperty<T>?,
        showSensitive: Boolean,
        mapper: (T) -> String,
    ) {
        if (property == null) return

        val value = property.value
        val text = if (showSensitive || !property.isSensitive) mapper(value) else "*****"
        row(label) { copyableLabel(text) }
    }
}
