package com.jetbrains.aspire.rider.sessions.projectLaunchers

import com.intellij.openapi.project.Project
import com.jetbrains.aspire.rider.sessions.findBySessionProject
import com.jetbrains.rider.model.RunnableProject
import com.jetbrains.rider.model.RunnableProjectKind
import com.jetbrains.rider.model.runnableProjectsModel
import com.jetbrains.rider.projectView.solution
import java.nio.file.Path

abstract class DotNetRunnableSessionProcessLauncher : DotNetSessionProcessLauncher() {
    protected abstract val supportedRunnableProjectKinds: List<RunnableProjectKind>

    override suspend fun isApplicable(projectPath: Path, project: Project): Boolean {
        val runnableProject = findSupportedRunnableProjectByPath(projectPath, project)
        return runnableProject != null
    }

    protected fun findSupportedRunnableProjectByPath(sessionProjectPath: Path, project: Project): RunnableProject? {
        return project.solution.runnableProjectsModel.findBySessionProject(sessionProjectPath) {
            supportedRunnableProjectKinds.contains(it.kind)
        }
    }
}