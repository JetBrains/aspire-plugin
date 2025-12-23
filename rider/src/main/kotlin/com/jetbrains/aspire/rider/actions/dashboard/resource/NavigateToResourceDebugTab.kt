package com.jetbrains.aspire.rider.actions.dashboard.resource

import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.ui.RunContentManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.progress.currentThreadCoroutineScope
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.jetbrains.aspire.actions.dashboard.resource.AspireResourceBaseAction
import com.jetbrains.aspire.dashboard.AspireResource
import com.jetbrains.aspire.sessions.SessionProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path

class NavigateToResourceDebugTab : AspireResourceBaseAction() {
    override fun performAction(resourceService: AspireResource, dataContext: DataContext, project: Project) {
        val projectPath = resourceService.projectPath?.value ?: return
        val debugSession = findDebugProfileByProject(projectPath, project) ?: return
        currentThreadCoroutineScope().launch {
            withContext(Dispatchers.EDT) {
                val contentDescriptor = debugSession.runContentDescriptor
                RunContentManager
                    .getInstance(project)
                    .toFrontRunContent(DefaultDebugExecutor.getDebugExecutorInstance(), contentDescriptor)
            }
        }
    }

    override fun updateAction(event: AnActionEvent, resourceService: AspireResource, project: Project) {
        val projectPath = resourceService.projectPath?.value
        val isUnderDebugger = resourceService.isUnderDebugger
        if (projectPath == null || isUnderDebugger != true) {
            event.presentation.isEnabledAndVisible = false
            return
        }

        val debugSession = findDebugProfileByProject(projectPath, project)
        if (debugSession == null) {
            event.presentation.isEnabledAndVisible = false
            return
        }

        event.presentation.isEnabledAndVisible = true
    }

    private fun findDebugProfileByProject(projectPath: Path, project: Project): XDebugSession? {
        val allSessions = XDebuggerManager.getInstance(project).debugSessions
        for (debugSession in allSessions) {
            val sessionRunProfile = debugSession.runProfile
            if (sessionRunProfile !is SessionProfile) continue
            if (sessionRunProfile.projectPath == projectPath) return debugSession
        }

        return null
    }
}