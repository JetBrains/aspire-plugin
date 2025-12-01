package com.jetbrains.aspire.dashboard.components

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.actionButton
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import com.jetbrains.aspire.AspireCoreBundle
import com.jetbrains.aspire.generated.ResourceState
import com.jetbrains.aspire.generated.ResourceType
import com.jetbrains.aspire.dashboard.AspireResource
import com.jetbrains.aspire.dashboard.RestartResourceCommand
import com.jetbrains.aspire.dashboard.StartResourceCommand
import com.jetbrains.aspire.dashboard.StopResourceCommand
import com.jetbrains.aspire.settings.AspireSettings
import com.jetbrains.aspire.util.getIcon
import kotlin.io.path.absolutePathString
import java.nio.file.Path

class ResourceDashboardPanel(aspireResource: AspireResource) : BorderLayoutPanel() {
    private var panel = setUpPanel(aspireResource)

    init {
        border = JBUI.Borders.empty(5, 10)
        add(ScrollPaneFactory.createScrollPane(panel, SideBorder.NONE))
    }

    private fun setUpPanel(resourceData: AspireResource): DialogPanel = panel {
        addHeader(resourceData)
        addProperties(resourceData)
        addEndpoints(resourceData)
        addVolumes(resourceData)
        addEnvironmentVariables(resourceData)
    }

    private fun Panel.addHeader(resourceData: AspireResource) {
        row {
            addIconAndTitle(resourceData)
            addStateAndHealth(resourceData)
            addActionButtons(resourceData)
        }
        separator()
    }

    private fun Row.addIconAndTitle(resourceData: AspireResource) {
        val resourceIcon = getIcon(resourceData)
        icon(resourceIcon)
            .gap(RightGap.SMALL)
        copyableLabel(resourceData.displayName)
            .bold()
            .gap(RightGap.SMALL)

        if (resourceData.type == ResourceType.Project) {
            resourceData.projectPath?.value?.let {
                copyableLabel(it.fileName.toString(), color = UIUtil.FontColor.BRIGHTER)
                    .gap(RightGap.SMALL)
            }
        }

        if (resourceData.type == ResourceType.Container) {
            resourceData.containerImage?.value?.let {
                copyableLabel(it, color = UIUtil.FontColor.BRIGHTER)
                    .gap(RightGap.SMALL)
            }
        }

        if (resourceData.type == ResourceType.Executable) {
            resourceData.executablePath?.value?.let {
                copyableLabel(it.fileName.toString(), color = UIUtil.FontColor.BRIGHTER)
                    .gap(RightGap.SMALL)
            }
        }
    }

    private fun Row.addStateAndHealth(resourceData: AspireResource) {
        val state = resourceData.state
        if (state != null) {
            separator()
                .gap(RightGap.SMALL)

            val healthStatus = resourceData.healthStatus
            val hasHealthStatus = state == ResourceState.Running && healthStatus != null

            copyableLabel(state.name, color = UIUtil.FontColor.BRIGHTER)
                .apply {
                    if (hasHealthStatus) gap(RightGap.SMALL)
                    else gap(RightGap.COLUMNS)
                }

            if (hasHealthStatus) {
                copyableLabel("(${healthStatus.name})", color = UIUtil.FontColor.BRIGHTER)
                    .gap(RightGap.COLUMNS)
            }
        }
    }

    private fun Row.addActionButtons(resourceData: AspireResource) {
        val startAction = ActionManager.getInstance().getAction("Aspire.Resource.Start")
        actionButton(startAction)
        val stopAction = ActionManager.getInstance().getAction("Aspire.Resource.Stop")
        actionButton(stopAction)
        if (resourceData.type == ResourceType.Project &&
            resourceData.projectPath?.value != null
        ) {
            val restartWithoutDebuggerAction =
                ActionManager.getInstance().getAction("Aspire.Resource.RestartWithoutDebugger")
            actionButton(restartWithoutDebuggerAction)

            val restartWithDebuggerAction =
                ActionManager.getInstance().getAction("Aspire.Resource.RestartWithDebugger")
            actionButton(restartWithDebuggerAction)
        } else {
            val restartAction = ActionManager.getInstance().getAction("Aspire.Resource.Restart")
            actionButton(restartAction)
        }
        if (resourceData.type == ResourceType.Project &&
            resourceData.state == ResourceState.Running &&
            resourceData.pid?.value != null &&
            resourceData.isUnderDebugger == false
        ) {
            val attachAction = ActionManager.getInstance().getAction("Aspire.Resource.Attach")
            actionButton(attachAction)
        }
        if (resourceData.type == ResourceType.Project &&
            resourceData.state == ResourceState.Running &&
            resourceData.isUnderDebugger == true
        ) {
            val attachAction = ActionManager.getInstance().getAction("Aspire.Resource.NavigateToDebugTab")
            actionButton(attachAction)
        }
        if (resourceData.commands.any {
                !it.name.equals(StartResourceCommand, true) &&
                        !it.name.equals(StopResourceCommand, true) &&
                        !it.name.equals(RestartResourceCommand, true)
            }) {
            val executeCommandAction = ActionManager.getInstance().getAction("Aspire.Resource.Execute.Command")
            actionButton(executeCommandAction)
        }
    }

    private fun Panel.addEndpoints(resourceData: AspireResource) {
        if (resourceData.urls.isNotEmpty()) {
            row {
                label(AspireCoreBundle.message("service.tab.dashboard.endpoints")).bold()
            }.bottomGap(BottomGap.SMALL)
            resourceData.urls
                .sortedBy { it.sortOrder }
                .forEach { url ->
                    if (!url.isInternal) {
                        val endpointName =
                            if (url.displayName.isNotEmpty()) url.displayName
                            else if (!url.endpointName.isNullOrEmpty()) url.endpointName
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

    private fun Panel.addProperties(resourceData: AspireResource) {
        row {
            label(AspireCoreBundle.message("service.tab.dashboard.properties")).bold()
        }.bottomGap(BottomGap.SMALL)
        with(resourceData) {
            state?.let {
                row(AspireCoreBundle.message("service.tab.dashboard.properties.state")) { copyableLabel(it.name) }
            }
            healthStatus?.let {
                row(AspireCoreBundle.message("service.tab.dashboard.properties.health.status")) { copyableLabel(it.name) }
            }
            createdAt?.let {
                row(AspireCoreBundle.message("service.tab.dashboard.properties.creation.time")) { copyableLabel(it.toString()) }
            }
            startedAt?.let {
                row(AspireCoreBundle.message("service.tab.dashboard.properties.start.time")) { copyableLabel(it.toString()) }
            }
            stoppedAt?.let {
                row(AspireCoreBundle.message("service.tab.dashboard.properties.stop.time")) { copyableLabel(it.toString()) }
            }
            intPropertyRow(AspireCoreBundle.message("service.tab.dashboard.properties.pid"), resourceData.pid)
            intPropertyRow(AspireCoreBundle.message("service.tab.dashboard.properties.exit.code"), resourceData.exitCode)
            pathPropertyRow(AspireCoreBundle.message("service.tab.dashboard.properties.project"), projectPath)
            pathPropertyRow(AspireCoreBundle.message("service.tab.dashboard.properties.executable"), executablePath)
            pathPropertyRow(AspireCoreBundle.message("service.tab.dashboard.properties.working.dir"), executableWorkDir)
            stringPropertyRow(AspireCoreBundle.message("service.tab.dashboard.properties.args"), args)
            stringPropertyRow(AspireCoreBundle.message("service.tab.dashboard.properties.container.image"), containerImage)
            stringPropertyRow(AspireCoreBundle.message("service.tab.dashboard.properties.container.id"), containerId)
            stringPropertyRow(AspireCoreBundle.message("service.tab.dashboard.properties.container.ports"), containerPorts)
            stringPropertyRow(AspireCoreBundle.message("service.tab.dashboard.properties.container.command"), containerCommand)
            stringPropertyRow(AspireCoreBundle.message("service.tab.dashboard.properties.container.args"), containerArgs)
            stringPropertyRow(AspireCoreBundle.message("service.tab.dashboard.properties.connection.string"), connectionString)
            stringPropertyRow(AspireCoreBundle.message("service.tab.dashboard.properties.source"), source)
            stringPropertyRow(AspireCoreBundle.message("service.tab.dashboard.properties.value"), value)
            separator()
        }
    }

    private fun Panel.addVolumes(resourceData: AspireResource) {
        if (resourceData.volumes.isNotEmpty()) {
            row {
                label(AspireCoreBundle.message("service.tab.dashboard.volumes")).bold()
            }.bottomGap(BottomGap.SMALL)
            resourceData.volumes
                .sortedBy { it.source }
                .forEach { volume ->
                    row {
                        copyableLabel("${volume.source} : ${volume.target}")
                            .gap(RightGap.SMALL)

                        copyableLabel(volume.mountType, color = UIUtil.FontColor.BRIGHTER)
                            .apply { if (volume.isReadOnly) gap(RightGap.SMALL) }

                        if (volume.isReadOnly) {
                            copyableLabel("(${AspireCoreBundle.message("service.tab.dashboard.volumes.readonly")})")
                        }
                    }
                }
            separator()
        }
    }

    private fun Panel.addEnvironmentVariables(resourceData: AspireResource) {
        val showEnvironment = AspireSettings.getInstance().showEnvironmentVariables
        if (resourceData.environment.isNotEmpty()) {
            row {
                label(AspireCoreBundle.message("service.tab.dashboard.environment")).bold()
            }.bottomGap(BottomGap.SMALL)
            resourceData.environment.forEach { variable ->
                val valueText = if (showEnvironment) (variable.value ?: "-") else "*****"
                row {
                    copyableLabel("${variable.key} = $valueText")
                }
            }
        }
    }

    private fun Panel.stringPropertyRow(label: String, property: AspireResource.AspireResourceProperty<String>?) {
        propertyRow(label, property) { it }
    }

    private fun Panel.intPropertyRow(label: String, property: AspireResource.AspireResourceProperty<Int>?) {
        propertyRow(label, property) { it.toString() }
    }

    private fun Panel.pathPropertyRow(label: String, property: AspireResource.AspireResourceProperty<Path>?) {
        propertyRow(label, property) { it.absolutePathString() }
    }

    private fun <T> Panel.propertyRow(
        label: String,
        property: AspireResource.AspireResourceProperty<T>?,
        mapper: (T) -> String,
    ) {
        if (property == null) return

        val value = property.value
        val showSensitive = AspireSettings.getInstance().showSensitiveProperties
        val text = if (showSensitive || !property.isSensitive) mapper(value) else "*****"
        row(label) { copyableLabel(text) }
    }
}