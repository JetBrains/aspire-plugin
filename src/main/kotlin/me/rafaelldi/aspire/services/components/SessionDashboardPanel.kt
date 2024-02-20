package me.rafaelldi.aspire.services.components

import com.intellij.ide.BrowserUtil
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import me.rafaelldi.aspire.AspireBundle
import me.rafaelldi.aspire.generated.ResourceModel
import me.rafaelldi.aspire.generated.ResourceType
import me.rafaelldi.aspire.util.getIcon
import kotlin.io.path.Path

class SessionDashboardPanel(resourceModel: ResourceModel) : BorderLayoutPanel() {
    init {
        border = JBUI.Borders.empty(5, 10)
        val panel = panel {
            row {
                val resourceIcon = getIcon(resourceModel.resourceType, resourceModel.state)
                icon(resourceIcon)
                    .gap(RightGap.SMALL)
                copyableLabel(resourceModel.displayName)
                    .bold()
                    .gap(RightGap.SMALL)

                if (resourceModel.resourceType == ResourceType.Project) {
                    resourceModel.properties.find { it.name.equals("project.path", true) }?.value?.let {
                        val path = Path(it.removeSurrounding("\""))
                        copyableLabel(path.fileName.toString(), color = UIUtil.FontColor.BRIGHTER)
                            .gap(RightGap.SMALL)
                    }
                    if (resourceModel.state?.equals("running", true) == true) {
                        resourceModel.properties.find { it.name.equals("executable.pid", true) }?.value?.let {
                            copyableLabel(it.removeSurrounding("\""), color = UIUtil.FontColor.BRIGHTER)
                                .gap(RightGap.SMALL)
                        }
                    } else {
                        resourceModel.properties.find { it.name.equals("resource.exitCode", true) }?.value?.let {
                            copyableLabel(it.removeSurrounding("\""), color = UIUtil.FontColor.BRIGHTER)
                                .gap(RightGap.SMALL)
                        }
                    }
                }
            }
            separator()
            row {
                label(AspireBundle.message("service.tab.dashboard.endpoints")).bold()
            }.bottomGap(BottomGap.SMALL)
            for (endpoint in resourceModel.endpoints) {
                row {
                    link(endpoint.proxyUrl) {
                        BrowserUtil.browse(endpoint.proxyUrl)
                    }
                }
            }
            separator()
            row {
                label(AspireBundle.message("service.tab.dashboard.properties")).bold()
            }.bottomGap(BottomGap.SMALL)
            for (property in resourceModel.properties) {
                row {
                    val value = property.value?.removeSurrounding("\"")
                    copyableLabel("${property.displayName ?: property.name} = ${value ?: "-"}")
                }
            }
            separator()
            row {
                label(AspireBundle.message("service.tab.dashboard.environment")).bold()
            }.bottomGap(BottomGap.SMALL)
            for (variable in resourceModel.environment) {
                row {
                    copyableLabel("${variable.key} = ${variable.value ?: "-"}")
                }
            }
        }

        add(ScrollPaneFactory.createScrollPane(panel, SideBorder.NONE))
    }
}