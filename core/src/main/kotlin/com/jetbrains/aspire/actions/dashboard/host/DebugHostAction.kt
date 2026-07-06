package com.jetbrains.aspire.actions.dashboard.host

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.currentThreadCoroutineScope
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.dashboard.AppHostUiState
import com.jetbrains.aspire.dashboard.AspireAppHostViewModel
import com.jetbrains.aspire.extensions.AspireAppHostLauncher
import com.jetbrains.aspire.worker.AspireAppHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DebugHostAction : AspireHostBaseAction() {
    override fun performAction(appHost: AspireAppHost, project: Project) {
        currentThreadCoroutineScope().launch(Dispatchers.Default) {
            AspireAppHostLauncher.getInstance()?.launchAppHost(appHost, true, project)
        }
    }

    override fun updateAction(event: AnActionEvent, appHostVm: AspireAppHostViewModel) {
        event.presentation.isVisible = true
        event.presentation.isEnabled = appHostVm.uiState.value !is AppHostUiState.Active
    }
}
