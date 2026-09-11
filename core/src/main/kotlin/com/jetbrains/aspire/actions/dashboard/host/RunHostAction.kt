package com.jetbrains.aspire.actions.dashboard.host

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.currentThreadCoroutineScope
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.extensions.AspireAppHostLauncher
import com.jetbrains.aspire.worker.AspireAppHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RunHostAction : AspireHostBaseAction() {
    override fun performAction(appHost: AspireAppHost, project: Project) {
        currentThreadCoroutineScope().launch(Dispatchers.Default) {
            AspireAppHostLauncher.getInstance()?.launchAppHost(appHost, false, project)
        }
    }

    override fun updateAction(event: AnActionEvent, appHost: AspireAppHost) {
        event.presentation.isVisible = true
        event.presentation.isEnabled = when (appHost.appHostState.value) {
            AspireAppHost.AspireAppHostState.Inactive,
            AspireAppHost.AspireAppHostState.Stopped -> true
            is AspireAppHost.AspireAppHostState.Starting,
            is AspireAppHost.AspireAppHostState.Started -> false
        }
    }
}
