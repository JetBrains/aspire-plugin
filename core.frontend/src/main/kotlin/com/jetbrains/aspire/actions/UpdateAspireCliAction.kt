package com.jetbrains.aspire.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.jetbrains.aspire.AspireCoreFrontendBundle
import com.jetbrains.aspire.cli.AspireCliSelfUpdateService
import com.jetbrains.aspire.cli.AspireCliTerminalRunner
import kotlinx.coroutines.launch

internal class UpdateAspireCliAction : DumbAwareAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        event.coroutineScope.launch {
            val commandLine = project.service<AspireCliSelfUpdateService>().prepareCommand() ?: return@launch
            project.service<AspireCliTerminalRunner>()
                .run(commandLine, AspireCoreFrontendBundle.message("cli.update.terminal.title"))
        }
    }

    override fun update(event: AnActionEvent) {
        val project = event.project
        event.presentation.isEnabledAndVisible = project != null && !project.isDefault && !project.isDisposed
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
