package com.jetbrains.rider.aspire.util

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.jetbrains.rider.runtime.RiderDotNetActiveRuntimeHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.nameWithoutExtension

/**
 * A service responsible for building .NET projects with `dotnet build` command line.
 */
@Service(Service.Level.PROJECT)
class DotNetBuildService(private val project: Project) {
    companion object {
        fun getInstance(project: Project): DotNetBuildService = project.service()

        private val LOG = logger<DotNetBuildService>()
    }

    suspend fun buildProjects(projectPaths: List<Path>) {
        LOG.trace { "Building ${projectPaths.size} project(s): ${projectPaths.map { it.fileName }}" }
        projectPaths.forEach { buildProject(it) }
    }

    private suspend fun buildProject(projectPath: Path) {
        val runtime = RiderDotNetActiveRuntimeHost.getInstance(project).dotNetCoreRuntime.value
        if (runtime == null) {
            LOG.warn("Unable to find active .NET runtime")
            return
        }

        val buildCommandLine = GeneralCommandLine()
            .withExePath(runtime.cliExePath)
            .withParameters(listOf("build", projectPath.absolutePathString()))
        LOG.trace { "Building project $buildCommandLine" }

        val buildOutput = withContext(Dispatchers.IO) {
            withBackgroundProgress(project, "Building ${projectPath.nameWithoutExtension}") {
                ExecUtil.execAndGetOutput(buildCommandLine)
            }
        }

        if (!buildOutput.checkSuccess(LOG)) {
            LOG.warn("Unable to build project ${projectPath.absolutePathString()}")
            return
        }
    }
}