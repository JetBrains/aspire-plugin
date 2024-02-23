package me.rafaelldi.aspire.services.components

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import me.rafaelldi.aspire.AspireBundle
import me.rafaelldi.aspire.generated.ResourceType
import me.rafaelldi.aspire.services.AspireResourceService
import me.rafaelldi.aspire.util.getIcon
import kotlin.io.path.absolutePathString

class ResourceDashboardPanel(resourceService: AspireResourceService) : BorderLayoutPanel() {
    private var panel = setUpPanel(resourceService)

    init {
        border = JBUI.Borders.empty(5, 10)
        add(ScrollPaneFactory.createScrollPane(panel, SideBorder.NONE))
    }

    private fun setUpPanel(resourceData: AspireResourceService): DialogPanel = panel {
        row {
            val resourceIcon = getIcon(resourceData.resourceType, resourceData.isRunning)
            icon(resourceIcon)
                .gap(RightGap.SMALL)
            copyableLabel(resourceData.displayName)
                .bold()
                .gap(RightGap.SMALL)

            if (resourceData.resourceType == ResourceType.Project) {
                resourceData.projectPath?.let {
                    copyableLabel(it.fileName.toString(), color = UIUtil.FontColor.BRIGHTER)
                        .gap(RightGap.SMALL)
                }
            }

            if (resourceData.resourceType == ResourceType.Container) {
                resourceData.containerImage?.let {
                    copyableLabel(it, color = UIUtil.FontColor.BRIGHTER)
                        .gap(RightGap.SMALL)
                }
            }

            if (resourceData.resourceType == ResourceType.Executable) {
                resourceData.executablePath?.let {
                    copyableLabel(it.fileName.toString(), color = UIUtil.FontColor.BRIGHTER)
                        .gap(RightGap.SMALL)
                }
            }

            val state = resourceData.state
            if (!state.isNullOrEmpty()) {
                separator()
                    .gap(RightGap.SMALL)
                copyableLabel(state, color = UIUtil.FontColor.BRIGHTER)
            }
        }
        separator()

        if (resourceData.endpoints.isNotEmpty()) {
            row {
                label(AspireBundle.message("service.tab.dashboard.endpoints")).bold()
            }.bottomGap(BottomGap.SMALL)
            for (endpoint in resourceData.endpoints) {
                row {
                    link(endpoint.proxyUrl) {
                        BrowserUtil.browse(endpoint.proxyUrl)
                    }
                }
            }
            separator()
        }

        row {
            label(AspireBundle.message("service.tab.dashboard.properties")).bold()
        }.bottomGap(BottomGap.SMALL)
        resourceData.state?.let {
            row(AspireBundle.message("service.tab.dashboard.properties.state")) { copyableLabel(it) }
        }
        resourceData.startTime?.let {
            row(AspireBundle.message("service.tab.dashboard.properties.start.time")) { copyableLabel(it.toString()) }
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

        if (resourceData.environment.isNotEmpty()) {
            row {
                label(AspireBundle.message("service.tab.dashboard.environment")).bold()
            }.bottomGap(BottomGap.SMALL)
            for (variable in resourceData.environment) {
                row {
                    copyableLabel("${variable.key} = ${variable.value ?: "-"}")
                }
            }
        }
    }
}