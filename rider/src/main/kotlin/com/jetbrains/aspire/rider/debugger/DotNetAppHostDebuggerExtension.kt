@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.rider.debugger

import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.platform.eel.EelProcessInfo
import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.PathUtil
import com.jetbrains.aspire.extensions.AppHostDebuggerExtension
import com.jetbrains.aspire.rider.AspireRiderBundle
import com.jetbrains.aspire.rider.sessions.findRunnableProjectByPathSuspending
import com.jetbrains.rd.ide.model.RdFileBasedProgramSource
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rd.util.threading.coroutines.synchronizeWith
import com.jetbrains.rider.ijent.extensions.toRd
import com.jetbrains.rider.model.ProjectOutput
import com.jetbrains.rider.run.configurations.dotNetFile.FileBasedProgramProjectManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.time.Duration.Companion.seconds

private val Path.pathWithoutExeOrDllExtension: Path
    get() = when (extension.lowercase()) {
        "exe", "dll" -> this.resolveSibling(this.nameWithoutExtension)
        else -> this
    }


internal class DotNetAppHostDebuggerExtension : AppHostDebuggerExtension {
    companion object {
        private val LOG = logger<DotNetAppHostDebuggerExtension>()

        private val POLL_INTERVAL = 1.seconds

        private val DOTNET_EXECUTABLE_NAMES = setOf("dotnet", "dotnet.exe")
    }

    override val priority: Int = 100

    override fun isApplicable(appHostFilePath: Path): Boolean =
        when (appHostFilePath.extension.lowercase()) {
            "csproj", "cs" -> true
            else -> false
        }

    override suspend fun attachToAppHost(
        appHostFilePath: Path,
        cliProcessPid: Long,
        project: Project,
        scope: CoroutineScope
    ): Result<Unit> {
        // Terminated when the run's scope is cancelled.
        val lifetime = LifetimeDefinition().also { it.synchronizeWith(scope) }
        val outputs = withBackgroundProgress(project, AspireRiderBundle.message("progress.waiting.for.apphost")) {
            resolveAppHostOutputs(appHostFilePath, project, lifetime)
        }

        if (outputs.isEmpty()) {
            LOG.warn("Could not find the .NET AppHost outputs for $appHostFilePath; skipping debugger attach")
            return Result.failure(Exception("Could not find the .NET AppHost outputs for $appHostFilePath"))
        }

        // Polls until the AppHost appears; the caller cancels the attempt once it has waited long enough.
        var pid: Long? = null
        while (pid == null) {
            pid = withContext(Dispatchers.IO) { findAppHostPid(outputs, cliProcessPid)?.pid?.value }
            if (pid == null) delay(POLL_INTERVAL)
        }

        LOG.trace { "Attaching .NET debugger to AppHost process $pid ($appHostFilePath)" }
        AttachDebuggerService.getInstance(project)
            .attach(pid.toInt(), AspireRiderBundle.message("progress.attach.debugger.to.apphost"))
        return Result.success(Unit)
    }

    /**
     * Resolves the paths a running AppHost can be recognized by, or `null` when the runnable project or its
     * outputs are not known (yet).
     */
    private suspend fun resolveAppHostOutputs(appHostFilePath: Path, project: Project, lifetime: Lifetime): List<ProjectOutput> {
        val projectPath = when (appHostFilePath.extension.lowercase()) {
            "csproj" -> appHostFilePath
            "cs" -> getFileBasedProjectPath(appHostFilePath, project, lifetime)
            else -> null
        }

        val outputPaths = projectPath?.let {
            withContext(Dispatchers.EDT) {
                findRunnableProjectByPathSuspending(projectPath, project)?.projectOutputs
            }
        }

        if (outputPaths.isNullOrEmpty()) {
            LOG.trace { "Unable to find the project output for $appHostFilePath" }
            return emptyList<ProjectOutput>()
        }

        return outputPaths
    }

    private suspend fun getFileBasedProjectPath(appHostFilePath: Path, project: Project, lifetime: Lifetime): Path? {
        val projectManager = FileBasedProgramProjectManager.getInstance(project)
        return projectManager.createProjectFile(RdFileBasedProgramSource(appHostFilePath.toRd()), lifetime)
    }

    /**
     * Returns the PID of the first descendant of [cliProcessPid] running the AppHost [outputs], or `null` when it
     * has not started yet. The subtree of a matching process is not searched.
     */
    private suspend fun findAppHostPid(outputs: List<ProjectOutput>, cliProcessPid: Long): EelProcessInfo? {
        val childrenByParent = localEel.exec.processManagement.listProcesses()
            .filter { it.parentPid != null }
            .groupBy { it.parentPid!!.value }

        val visited = mutableSetOf(cliProcessPid)
        val queue = ArrayDeque<Long>().apply { add(cliProcessPid) }
        while (queue.isNotEmpty()) {
            val children = childrenByParent[queue.removeFirst()] ?: continue
            for (child in children) {
                // Guards against cycles caused by PID reuse in the snapshot.
                if (!visited.add(child.pid.value)) continue
                if (isAppHostProcess(child, outputs)) return child
                queue.add(child.pid.value)
            }
        }

        return null
    }

    private suspend fun isAppHostProcess(process: EelProcessInfo, outputs: List<ProjectOutput>): Boolean {
        val executable = process.executable ?: return false
        val outputPaths = outputs.mapNotNull { it.exePath.toNioPathOrNull()?.pathWithoutExeOrDllExtension }
        if (executable.toNioPathOrNull()?.normalize()?.pathWithoutExeOrDllExtension in outputPaths) return true

        // `dotnet <assembly>` / `dotnet exec ... <assembly>`. Arguments are fetched lazily, so only `dotnet`
        // processes are inspected.
        if (PathUtil.getFileName(executable).lowercase() !in DOTNET_EXECUTABLE_NAMES) return false
        return process.arguments.await().any { it.toNioPathOrNull()?.pathWithoutExeOrDllExtension?.normalize() in outputPaths }
    }

}
