package me.rafaelldi.aspire.services.components

import com.intellij.icons.AllIcons
import com.intellij.ide.actions.RevealFileAction
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import me.rafaelldi.aspire.AspireBundle
import me.rafaelldi.aspire.services.SessionServiceData
import java.awt.datatransfer.StringSelection
import kotlin.io.path.Path

class SessionDashboardPanel(sessionData: SessionServiceData) : BorderLayoutPanel() {
    init {
        border = JBUI.Borders.empty(5, 10)
        val panel = panel {
            row {
                val projectName = sessionData.sessionModel.telemetryServiceName ?: "-"
                label(AspireBundle.message("service.tab.information.name"))
                    .bold()
                copyableLabel(projectName)
                    .gap(RightGap.SMALL)
                inlineIconButton(
                    AllIcons.General.InlineCopyHover,
                    AllIcons.General.InlineCopy,
                    projectName
                ) {
                    CopyPasteManager.getInstance().setContents(StringSelection(it))
                }
            }
            row {
                val projectPath = sessionData.sessionModel.projectPath
                label(AspireBundle.message("service.tab.information.project"))
                    .bold()
                copyableLabel(projectPath)
                    .gap(RightGap.SMALL)
                inlineIconButton(
                    AllIcons.General.InlineCopyHover,
                    AllIcons.General.InlineCopy,
                    projectPath
                ) {
                    CopyPasteManager.getInstance().setContents(StringSelection(it))
                }
                    .gap(RightGap.SMALL)
                inlineIconButton(
                    AllIcons.General.OpenDisk,
                    AllIcons.General.OpenDiskHover,
                    sessionData.sessionModel.projectPath
                ) {
                    RevealFileAction.openFile(Path(it))
                }
            }
            row {
                val args = sessionData.sessionModel.args?.joinToString() ?: "-"
                label(AspireBundle.message("service.tab.information.arguments"))
                    .bold()
                copyableLabel(args)
                    .gap(RightGap.SMALL)
                inlineIconButton(
                    AllIcons.General.InlineCopyHover,
                    AllIcons.General.InlineCopy,
                    args
                ) {
                    CopyPasteManager.getInstance().setContents(StringSelection(it))
                }
                    .gap(RightGap.SMALL)
            }
        }

        add(ScrollPaneFactory.createScrollPane(panel, SideBorder.NONE))
    }
}