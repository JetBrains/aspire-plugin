package com.jetbrains.aspire.rider.sessions

import com.intellij.openapi.project.Project
import com.intellij.util.io.systemIndependentPath
import com.jetbrains.rd.util.threading.coroutines.nextNotNullValue
import com.jetbrains.rider.model.RunnableProject
import com.jetbrains.rider.model.RunnableProjectsModel
import com.jetbrains.rider.model.runnableProjectsModel
import com.jetbrains.rider.projectView.solution
import java.nio.file.Path

suspend fun findRunnableProjectByPath(
    projectPath: Path,
    project: Project,
    runnablePredicate: (RunnableProject) -> Boolean = { true }
): RunnableProject? {
    return project.solution.runnableProjectsModel.projects.nextNotNullValue().findByProjectPath(projectPath, runnablePredicate)
}

fun RunnableProjectsModel.findByProjectPath(
    projectPath: Path,
    runnablePredicate: (RunnableProject) -> Boolean = { true }
): RunnableProject? = projects.valueOrNull?.findByProjectPath(projectPath, runnablePredicate)

private fun List<RunnableProject>.findByProjectPath(
    projectPath: Path,
    runnablePredicate: (RunnableProject) -> Boolean = { true }
): RunnableProject? {
    val runnableProjects = this
    val projectPathString = projectPath.systemIndependentPath
    return runnableProjects.firstOrNull {
        it.projectFilePath == projectPathString && runnablePredicate(it)
    }
}
