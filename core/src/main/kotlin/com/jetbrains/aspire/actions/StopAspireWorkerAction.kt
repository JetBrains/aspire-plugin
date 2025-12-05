@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.currentThreadCoroutineScope
import com.jetbrains.aspire.worker.AspireWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class StopAspireWorkerAction: AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        currentThreadCoroutineScope().launch(Dispatchers.Default) {
            AspireWorker.getInstance(project).stop()
        }
    }
}