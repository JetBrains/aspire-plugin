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
import com.jetbrains.aspire.worker.AspireResource
import com.jetbrains.aspire.rider.sessions.SessionProfileModeService
import com.jetbrains.aspire.rider.sessions.projectLaunchers.DotNetSessionProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.absolutePathString

class NavigateToResourceDebugTab : AspireResourceBaseAction() {
    override fun performAction(resourceService: AspireResource, dataContext: DataContext, project: Project) {
        val projectPath = resourceService.data.projectPath?.value ?: return
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
        val projectPath = resourceService.data.projectPath?.value
        if (projectPath == null) {
            event.presentation.isEnabledAndVisible = false
            return
        }

        val isUnderDebugger = SessionProfileModeService
            .getInstance(project)
            .isSessionProfileUnderDebugger(projectPath.absolutePathString())
        if (isUnderDebugger != true) {
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
            if (sessionRunProfile !is DotNetSessionProfile) continue
            if (sessionRunProfile.projectPath == projectPath) return debugSession
        }

        return null
    }
}