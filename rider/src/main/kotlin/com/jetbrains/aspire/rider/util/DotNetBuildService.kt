@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.rider.util

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.withBackgroundProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.nameWithoutExtension

/**
 * A service responsible for building .NET projects with `dotnet build` command line.
 */
@Service(Service.Level.PROJECT)
internal class DotNetBuildService(private val project: Project) {
    companion object {
        fun getInstance(project: Project): DotNetBuildService = project.service()

        private val LOG = logger<DotNetBuildService>()
    }

    /**
     * Builds the project with the .NET CLI of the active .NET runtime.
     */
    suspend fun buildProject(projectPath: Path): Result<Unit> {
        val dotNetCliPath = getActiveDotNetCliPath(project).getOrElse { return Result.failure(it) }
        return buildProject(projectPath, dotNetCliPath)
    }

    /**
     * Builds the project with the given .NET CLI.
     */
    suspend fun buildProject(projectPath: Path, dotNetCliPath: Path): Result<Unit> {
        val commandLine = GeneralCommandLine()
            .withExePath(dotNetCliPath.absolutePathString())
            .withParameters("build", projectPath.absolutePathString())
        LOG.trace { "Building project $commandLine" }

        val output = try {
            withContext(Dispatchers.IO) {
                withBackgroundProgress(project, "Building ${projectPath.nameWithoutExtension}") {
                    ExecUtil.execAndGetOutput(commandLine)
                }
            }
        } catch (e: ExecutionException) {
            return Result.failure(e)
        }

        if (output.exitCode != 0) {
            val details = output.stderr.ifBlank { output.stdout }.trim()
            return Result.failure(
                ExecutionException("Unable to build project $projectPath (exit code ${output.exitCode})\n$details")
            )
        }

        return Result.success(Unit)
    }
}
