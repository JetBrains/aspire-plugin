package com.jetbrains.aspire.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.currentThreadCoroutineScope
import com.intellij.platform.backend.workspace.virtualFile
import com.jetbrains.aspire.manifest.ManifestService
import com.jetbrains.aspire.util.isAspireHostProject
import com.jetbrains.rider.projectView.workspace.getProjectModelEntity
import com.jetbrains.rider.runtime.RiderDotNetActiveRuntimeHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ManifestGenerationAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val entity = event.dataContext.getProjectModelEntity() ?: return
        if (!entity.isAspireHostProject()) return
        val file = entity.url?.virtualFile ?: return

        currentThreadCoroutineScope().launch(Dispatchers.Default) {
            ManifestService.getInstance(project).generateManifest(file.toNioPath())
        }
    }

    override fun update(event: AnActionEvent) {
        val project = event.project
        if (project == null) {
            event.presentation.isEnabledAndVisible = false
            return
        }

        val runtime = RiderDotNetActiveRuntimeHost.getInstance(project).dotNetCoreRuntime.value
        if (runtime == null) {
            event.presentation.isEnabledAndVisible = false
            return
        }

        val entity = event.dataContext.getProjectModelEntity()
        if (entity == null || !entity.isAspireHostProject()) {
            event.presentation.isEnabledAndVisible = false
            return
        }

        event.presentation.isEnabledAndVisible = true
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}