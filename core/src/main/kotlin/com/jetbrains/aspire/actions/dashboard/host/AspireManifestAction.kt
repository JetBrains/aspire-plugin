package com.jetbrains.aspire.actions.dashboard.host

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.currentThreadCoroutineScope
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.dashboard.AspireAppHostViewModel
import com.jetbrains.aspire.manifest.ManifestService
import com.jetbrains.aspire.worker.AspireAppHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AspireManifestAction : AspireHostBaseAction() {
    override fun performAction(appHost: AspireAppHost, project: Project) {
        val mainFilePath = appHost.mainFilePath
        currentThreadCoroutineScope().launch(Dispatchers.Default) {
            ManifestService.getInstance(project).generateManifest(mainFilePath)
        }
    }

    override fun updateAction(event: AnActionEvent, appHostVm: AspireAppHostViewModel) {
        event.presentation.isEnabledAndVisible = true
    }
}