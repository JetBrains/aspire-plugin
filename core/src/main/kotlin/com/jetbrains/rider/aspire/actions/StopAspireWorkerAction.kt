@file:Suppress("UnstableApiUsage")

package com.jetbrains.rider.aspire.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.currentThreadCoroutineScope
import com.jetbrains.rider.aspire.worker.AspireWorkerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class StopAspireWorkerAction: AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val manager = AspireWorkerManager.getInstance(project)
        currentThreadCoroutineScope().launch(Dispatchers.Default) {
            manager.stopAspireWorker()
        }
    }
}