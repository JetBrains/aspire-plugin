package com.jetbrains.rider.aspire.sessionHost

import com.intellij.openapi.project.Project
import com.intellij.util.io.systemIndependentPath
import com.jetbrains.rider.model.RunnableProject
import com.jetbrains.rider.model.RunnableProjectsModel
import com.jetbrains.rider.model.runnableProjectsModel
import com.jetbrains.rider.projectView.solution
import java.nio.file.Path

fun findRunnableProjectByPath(
    projectPath: Path,
    project: Project,
    runnablePredicate: (RunnableProject) -> Boolean = { true }
): RunnableProject? {
    return project.solution.runnableProjectsModel.findBySessionProject(projectPath, runnablePredicate)
}

fun RunnableProjectsModel.findBySessionProject(
    sessionProjectPath: Path,
    runnablePredicate: (RunnableProject) -> Boolean = { true }
): RunnableProject? {
    val runnableProjects = projects.valueOrNull ?: return null
    val sessionProjectPathString = sessionProjectPath.systemIndependentPath
    return runnableProjects.singleOrNull {
        it.projectFilePath == sessionProjectPathString && runnablePredicate(it)
    }
}