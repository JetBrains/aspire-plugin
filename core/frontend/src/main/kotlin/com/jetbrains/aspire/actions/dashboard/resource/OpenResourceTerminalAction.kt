package com.jetbrains.aspire.actions.dashboard.resource

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.AspireCoreFrontendBundle
import com.jetbrains.aspire.cli.AspireCliCommand
import com.jetbrains.aspire.cli.AspireCliCommandRunner
import com.jetbrains.aspire.worker.AspireResourceData
import kotlinx.coroutines.launch

/**
 * Attaches to the interactive terminal of a resource with `WithTerminal()` by running `aspire terminal attach`
 * in a new tab of the Terminal tool window.
 */
internal class OpenResourceTerminalAction : AspireResourceBaseAction() {
    override fun performAction(event: AnActionEvent, resourceData: AspireResourceData, project: Project) {
        if (!resourceData.isTerminalEnabled) return

        val command = AspireCliCommand.TerminalAttach(resourceData.id.appHostId, resourceData.name)
        val tabName = AspireCoreFrontendBundle.message("resource.terminal.title", resourceData.name)
        event.coroutineScope.launch {
            AspireCliCommandRunner.getInstance(project).run(command, tabName, startWhenTabShown = true)
        }
    }

    override fun updateAction(event: AnActionEvent, resourceData: AspireResourceData, project: Project) {
        event.presentation.isEnabledAndVisible = resourceData.isTerminalEnabled
    }
}
