package com.jetbrains.rider.aspire.sessionHost

import com.intellij.util.io.systemIndependentPath
import com.jetbrains.rider.model.RunnableProject
import com.jetbrains.rider.model.RunnableProjectsModel
import java.nio.file.Path

fun RunnableProjectsModel.findBySessionProject(
    sessionProjectPath: Path,
    runnablePredicate: (RunnableProject) -> Boolean
): RunnableProject? {
    val runnableProjects = projects.valueOrNull ?: return null
    val sessionProjectPathString = sessionProjectPath.systemIndependentPath
    return runnableProjects.singleOrNull {
        it.projectFilePath == sessionProjectPathString && runnablePredicate(it)
    }
}