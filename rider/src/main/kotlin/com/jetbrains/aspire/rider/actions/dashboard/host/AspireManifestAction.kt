package com.jetbrains.aspire.rider.actions.dashboard.host

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.actions.dashboard.host.AspireAppHostBaseAction
import com.jetbrains.aspire.rider.manifest.ManifestService
import com.jetbrains.aspire.worker.AspireAppHostData
import com.jetbrains.aspire.worker.AspireWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AspireManifestAction : AspireAppHostBaseAction() {
    override fun performAction(event: AnActionEvent, appHostData: AspireAppHostData, project: Project) {
        val mainFilePath = AspireWorker.getInstance(project).getAppHostById(appHostData.id)?.appHostPath ?: return
        event.coroutineScope.launch(Dispatchers.Default) {
            ManifestService.getInstance(project).generateManifest(mainFilePath)
        }
    }

    override fun updateAction(event: AnActionEvent, appHostData: AspireAppHostData) {
        event.presentation.isEnabledAndVisible = true
    }
}
