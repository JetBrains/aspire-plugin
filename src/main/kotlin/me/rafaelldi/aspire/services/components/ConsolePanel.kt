package me.rafaelldi.aspire.services.components

import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.util.launchBackground
import com.intellij.openapi.rd.util.withUiContext
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import me.rafaelldi.aspire.services.SessionServiceData

class ConsolePanel(sessionData: SessionServiceData, project: Project) : BorderLayoutPanel(), Disposable {
    private val consoleView: ConsoleView = TextConsoleBuilderFactory
        .getInstance()
        .createBuilder(project)
        .apply { setViewer(true) }
        .console

    init {
        border = JBUI.Borders.empty()
        add(consoleView.component)

        sessionData.sessionLifetime.launchBackground {
            sessionData.sessionEvents.collect {
                withUiContext {
                    consoleView.print(
                        it.message,
                        if (!it.isStdErr) ConsoleViewContentType.NORMAL_OUTPUT
                        else ConsoleViewContentType.ERROR_OUTPUT
                    )
                }
            }
        }

        Disposer.register(this, consoleView)
    }

    override fun dispose() {
    }
}