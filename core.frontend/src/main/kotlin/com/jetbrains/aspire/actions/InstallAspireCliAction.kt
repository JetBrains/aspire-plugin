package com.jetbrains.aspire.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.jetbrains.aspire.cli.AspireCliInstallationService
import com.jetbrains.aspire.cli.AspireCliInstallationTerminalRunner
import kotlinx.coroutines.launch

internal class InstallAspireCliAction : DumbAwareAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        event.coroutineScope.launch {
            val commandLine = project.service<AspireCliInstallationService>().prepareCommand() ?: return@launch
            project.service<AspireCliInstallationTerminalRunner>().run(commandLine)
        }
    }

    override fun update(event: AnActionEvent) {
        val project = event.project
        event.presentation.isEnabledAndVisible = project != null && !project.isDefault && !project.isDisposed
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
