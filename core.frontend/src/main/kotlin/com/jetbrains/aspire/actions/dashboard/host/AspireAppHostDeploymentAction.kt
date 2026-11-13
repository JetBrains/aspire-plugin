package com.jetbrains.aspire.actions.dashboard.host

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.jetbrains.aspire.cli.AspireCliCommand
import com.jetbrains.aspire.cli.AspireCliCommandRunner
import com.jetbrains.aspire.worker.AspireAppHostData
import com.jetbrains.aspire.worker.AspireAppHostId
import kotlinx.coroutines.launch

internal abstract class AspireAppHostDeploymentAction(
    private val createCommand: (AspireAppHostId) -> AspireCliCommand.AppHostCommand,
    private val tabName: (appHostName: String) -> @NlsContexts.TabTitle String,
) : AspireAppHostBaseAction() {
    override fun performAction(event: AnActionEvent, appHostData: AspireAppHostData, project: Project) {
        val command = createCommand(appHostData.id)
        val tabName = tabName(appHostData.name)
        event.coroutineScope.launch {
            AspireCliCommandRunner.getInstance(project).run(command, tabName)
        }
    }

    override fun updateAction(event: AnActionEvent, appHostData: AspireAppHostData) {
        event.presentation.isEnabledAndVisible = true
    }
}
