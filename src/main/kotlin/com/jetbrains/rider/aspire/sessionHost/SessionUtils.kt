package com.jetbrains.rider.aspire.sessionHost

import com.intellij.util.io.systemIndependentPath
import com.jetbrains.rider.model.RunnableProject
import com.jetbrains.rider.model.RunnableProjectsModel
import com.jetbrains.rider.run.configurations.RunnableProjectKinds
import java.nio.file.Path

fun RunnableProjectsModel.findBySessionProject(sessionProjectPath: Path): RunnableProject? {
    val runnableProjects = projects.valueOrNull ?: return null
    val sessionProjectPathString = sessionProjectPath.systemIndependentPath
    return runnableProjects.singleOrNull {
        it.projectFilePath == sessionProjectPathString && it.kind == RunnableProjectKinds.DotNetCore
    }
}