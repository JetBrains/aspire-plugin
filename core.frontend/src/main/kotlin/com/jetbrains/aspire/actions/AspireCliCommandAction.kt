package com.jetbrains.aspire.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.NlsContexts
import com.jetbrains.aspire.cli.AspireCliCommand
import com.jetbrains.aspire.cli.AspireCliCommandRunner
import kotlinx.coroutines.launch

/**
 * A project-level action that runs an [AspireCliCommand] in a terminal.
 */
internal abstract class AspireCliCommandAction(
    private val command: AspireCliCommand,
    private val tabName: () -> @NlsContexts.TabTitle String,
) : DumbAwareAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        event.coroutineScope.launch {
            AspireCliCommandRunner.getInstance(project).run(command, tabName())
        }
    }

    override fun update(event: AnActionEvent) {
        val project = event.project
        event.presentation.isEnabledAndVisible = project != null && !project.isDefault && !project.isDisposed
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
