package com.jetbrains.rider.aspire.services.components

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.actionButton
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import com.jetbrains.rider.aspire.AspireBundle
import com.jetbrains.rider.aspire.generated.ResourceState
import com.jetbrains.rider.aspire.generated.ResourceType
import com.jetbrains.rider.aspire.services.AspireResource
import com.jetbrains.rider.aspire.services.RestartResourceCommand
import com.jetbrains.rider.aspire.services.StartResourceCommand
import com.jetbrains.rider.aspire.services.StopResourceCommand
import com.jetbrains.rider.aspire.util.getIcon
import kotlin.io.path.absolutePathString

class ResourceDashboardPanel(aspireResource: AspireResource) : BorderLayoutPanel() {
    private var panel = setUpPanel(aspireResource)

    init {
        border = JBUI.Borders.empty(5, 10)
        add(ScrollPaneFactory.createScrollPane(panel, SideBorder.NONE))
    }

    private fun setUpPanel(resourceData: AspireResource): DialogPanel = panel {
        row {
            val resourceIcon = getIcon(resourceData)
            icon(resourceIcon)
                .gap(RightGap.SMALL)
            copyableLabel(resourceData.displayName)
                .bold()
                .gap(RightGap.SMALL)

            if (resourceData.type == ResourceType.Project) {
                resourceData.projectPath?.let {
                    copyableLabel(it.fileName.toString(), color = UIUtil.FontColor.BRIGHTER)
                        .gap(RightGap.SMALL)
                }
            }

            if (resourceData.type == ResourceType.Container) {
                resourceData.containerImage?.let {
                    copyableLabel(it, color = UIUtil.FontColor.BRIGHTER)
                        .gap(RightGap.SMALL)
                }
            }

            if (resourceData.type == ResourceType.Executable) {
                resourceData.executablePath?.let {
                    copyableLabel(it.fileName.toString(), color = UIUtil.FontColor.BRIGHTER)
                        .gap(RightGap.SMALL)
                }
            }

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

            val startAction = ActionManager.getInstance().getAction("Aspire.Resource.Start")
            actionButton(startAction)
            val restartAction = ActionManager.getInstance().getAction("Aspire.Resource.Restart")
            actionButton(restartAction)
            val stopAction = ActionManager.getInstance().getAction("Aspire.Resource.Stop")
            actionButton(stopAction)
            if (resourceData.type == ResourceType.Project &&
                resourceData.state == ResourceState.Running &&
                resourceData.pid != null &&
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
        separator()

        if (resourceData.urls.isNotEmpty()) {
            row {
                label(AspireBundle.message("service.tab.dashboard.endpoints")).bold()
            }.bottomGap(BottomGap.SMALL)
            resourceData.urls
                .sortedBy { it.sortOrder }
                .forEach { url ->
                    if (!url.isInternal) {
                        val endpointName =
                            if (url.displayName.isNotEmpty()) url.displayName
                            else if (!url.endpointName.isNullOrEmpty()) url.endpointName
                            else ""
                        row(endpointName) {
                            link(url.fullUrl) {
                                BrowserUtil.browse(url.fullUrl)
                            }
                        }
                    }
                }
            separator()
        }

        row {
            label(AspireBundle.message("service.tab.dashboard.properties")).bold()
        }.bottomGap(BottomGap.SMALL)
        resourceData.state?.let {
            row(AspireBundle.message("service.tab.dashboard.properties.state")) { copyableLabel(it.name) }
        }
        resourceData.healthStatus?.let {
            row(AspireBundle.message("service.tab.dashboard.properties.health.status")) { copyableLabel(it.name) }
        }
        resourceData.createdAt?.let {
            row(AspireBundle.message("service.tab.dashboard.properties.creation.time")) { copyableLabel(it.toString()) }
        }
        resourceData.startedAt?.let {
            row(AspireBundle.message("service.tab.dashboard.properties.start.time")) { copyableLabel(it.toString()) }
        }
        resourceData.stoppedAt?.let {
            row(AspireBundle.message("service.tab.dashboard.properties.stop.time")) { copyableLabel(it.toString()) }
        }
        resourceData.pid?.let {
            row(AspireBundle.message("service.tab.dashboard.properties.pid")) { copyableLabel(it.toString()) }
        }
        resourceData.exitCode?.let {
            if (it != -1) {
                row(AspireBundle.message("service.tab.dashboard.properties.exit.code")) { copyableLabel(it.toString()) }
            }
        }
        resourceData.projectPath?.let {
            row(AspireBundle.message("service.tab.dashboard.properties.project")) { copyableLabel(it.absolutePathString()) }
        }
        resourceData.executablePath?.let {
            row(AspireBundle.message("service.tab.dashboard.properties.executable")) { copyableLabel(it.absolutePathString()) }
        }
        resourceData.executableWorkDir?.let {
            row(AspireBundle.message("service.tab.dashboard.properties.working.dir")) { copyableLabel(it.absolutePathString()) }
        }
        resourceData.args?.let {
            row(AspireBundle.message("service.tab.dashboard.properties.args")) { copyableLabel(it) }
        }
        resourceData.containerImage?.let {
            row(AspireBundle.message("service.tab.dashboard.properties.container.image")) { copyableLabel(it) }
        }
        resourceData.containerId?.let {
            row(AspireBundle.message("service.tab.dashboard.properties.container.id")) { copyableLabel(it) }
        }
        resourceData.containerPorts?.let {
            row(AspireBundle.message("service.tab.dashboard.properties.container.ports")) { copyableLabel(it) }
        }
        resourceData.containerCommand?.let {
            row(AspireBundle.message("service.tab.dashboard.properties.container.command")) { copyableLabel(it) }
        }
        resourceData.containerArgs?.let {
            row(AspireBundle.message("service.tab.dashboard.properties.container.args")) { copyableLabel(it) }
        }
        separator()

        if (resourceData.volumes.isNotEmpty()) {
            row {
                label(AspireBundle.message("service.tab.dashboard.volumes")).bold()
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
                            copyableLabel("(${AspireBundle.message("service.tab.dashboard.volumes.readonly")})")
                        }
                    }
                }
            separator()
        }

        if (resourceData.environment.isNotEmpty()) {
            row {
                label(AspireBundle.message("service.tab.dashboard.environment")).bold()
            }.bottomGap(BottomGap.SMALL)
            resourceData.environment.forEach { variable ->
                row {
                    copyableLabel("${variable.key} = ${variable.value ?: "-"}")
                }
            }
        }
    }
}