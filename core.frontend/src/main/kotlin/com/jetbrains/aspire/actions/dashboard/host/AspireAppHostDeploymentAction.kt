package com.jetbrains.aspire.actions.dashboard.host

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.deployment.AspireAppHostDeploymentService
import com.jetbrains.aspire.deployment.AspireDeploymentOperation
import com.jetbrains.aspire.deployment.AspireDeploymentTerminalRunner
import com.jetbrains.aspire.worker.AspireAppHostData
import kotlinx.coroutines.launch

internal abstract class AspireAppHostDeploymentAction(
    private val operation: AspireDeploymentOperation,
) : AspireAppHostBaseAction() {
    override fun performAction(event: AnActionEvent, appHostData: AspireAppHostData, project: Project) {
        event.coroutineScope.launch {
            val commandLine = project.service<AspireAppHostDeploymentService>()
                .prepareCommand(appHostData.id, operation) ?: return@launch
            project.service<AspireDeploymentTerminalRunner>().run(operation, commandLine)
        }
    }

    override fun updateAction(event: AnActionEvent, appHostData: AspireAppHostData) {
        event.presentation.isEnabledAndVisible = true
    }
}
