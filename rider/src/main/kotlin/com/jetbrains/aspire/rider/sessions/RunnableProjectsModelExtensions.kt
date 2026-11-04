package com.jetbrains.aspire.rider.sessions

import com.intellij.openapi.project.Project
import com.intellij.util.io.systemIndependentPath
import com.jetbrains.rd.util.threading.coroutines.nextNotNullValue
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

suspend fun findRunnableProjectByPathSuspending(
    projectPath: Path,
    project: Project,
    runnablePredicate: (RunnableProject) -> Boolean = { true }
): RunnableProject? {
    return project.solution.runnableProjectsModel.projects.nextNotNullValue().findBySessionProject(projectPath, runnablePredicate)
}

fun RunnableProjectsModel.findBySessionProject(
    sessionProjectPath: Path,
    runnablePredicate: (RunnableProject) -> Boolean = { true }
): RunnableProject? = projects.valueOrNull?.findBySessionProject(sessionProjectPath, runnablePredicate)

private fun List<RunnableProject>.findBySessionProject(
    sessionProjectPath: Path,
    runnablePredicate: (RunnableProject) -> Boolean = { true }
): RunnableProject? {
    val runnableProjects = this
    val sessionProjectPathString = sessionProjectPath.systemIndependentPath
    return runnableProjects.firstOrNull {
        it.projectFilePath == sessionProjectPathString && runnablePredicate(it)
    }
}
