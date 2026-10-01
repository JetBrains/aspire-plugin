@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.rider.debugger

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelProcessInfo
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.PathUtil
import com.jetbrains.aspire.rider.AspireRiderBundle
import com.jetbrains.aspire.rider.sessions.findRunnableProjectByPath
import com.jetbrains.aspire.rider.util.pathWithoutExeOrDllExtension
import com.jetbrains.rd.ide.model.RdFileBasedProgramSource
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rider.ijent.extensions.toRd
import com.jetbrains.rider.model.ProjectOutput
import com.jetbrains.rider.run.configurations.dotNetFile.FileBasedProgramProjectManager
import kotlinx.coroutines.*
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.time.Duration.Companion.seconds



@Service(Service.Level.PROJECT)
internal class DotNetAppHostDebuggerService(private val project: Project) {
    companion object {
        private val POLL_INTERVAL = 1.seconds
        private val DOTNET_EXECUTABLE_NAMES = setOf("dotnet", "dotnet.exe")

        private val LOG = logger<DotNetAppHostDebuggerService>()

        fun getInstance(project: Project): DotNetAppHostDebuggerService = project.service()
    }

    suspend fun attachToAppHost(appHostFilePath: Path, cliProcessPid: Long): Result<Unit> = coroutineScope {
        val lifetime = LifetimeDefinition()
        coroutineContext.job.invokeOnCompletion { lifetime.terminate() }

        return@coroutineScope withBackgroundProgress(project, AspireRiderBundle.message("progress.waiting.for.apphost")) {
            waitForAppHostProcess(appHostFilePath, cliProcessPid, lifetime)
        }.map { pid ->
            LOG.trace { "Attaching .NET debugger to AppHost process $pid ($appHostFilePath)" }
            AttachDebuggerService.getInstance(project)
                .attach(pid.toInt(), AspireRiderBundle.message("progress.attach.debugger.to.apphost"))
        }
    }

    private suspend fun waitForAppHostProcess(appHostFilePath: Path, cliProcessPid: Long, lifetime: Lifetime): Result<Long> {
        val outputs = resolveAppHostOutputs(appHostFilePath, lifetime)
        if (outputs.isEmpty()) {
            LOG.warn("Could not find the .NET AppHost outputs for $appHostFilePath; skipping debugger attach")
            return Result.failure(Exception("Could not find the .NET AppHost outputs for $appHostFilePath"))
        }

        val eelApi = project.getEelDescriptor().toEelApi()
        var pid: Long? = null
        while (pid == null) {
            pid = withContext(Dispatchers.IO) { findAppHostPid(outputs, cliProcessPid, eelApi)?.pid?.value }
            if (pid == null) delay(POLL_INTERVAL)
        }
        return Result.success(pid)
    }

    private suspend fun resolveAppHostOutputs(appHostFilePath: Path, lifetime: Lifetime): List<ProjectOutput> {
        val projectPath = when (appHostFilePath.extension.lowercase()) {
            "csproj" -> appHostFilePath
            "cs" -> getFileBasedProjectPath(appHostFilePath, lifetime)
            else -> null
        }

        val outputPaths = projectPath?.let {
            withContext(Dispatchers.EDT) {
                findRunnableProjectByPath(projectPath, project)?.projectOutputs
            }
        }

        return outputPaths.orEmpty()
    }

    private suspend fun getFileBasedProjectPath(appHostFilePath: Path, lifetime: Lifetime): Path? {
        val projectManager = FileBasedProgramProjectManager.getInstance(project)
        return projectManager.createProjectFile(RdFileBasedProgramSource(appHostFilePath.toRd()), lifetime)
    }

    private suspend fun findAppHostPid(outputs: List<ProjectOutput>, cliProcessPid: Long, eelApi: EelApi): EelProcessInfo? {
        val childrenByParent = eelApi.exec.processManagement.listProcesses()
            .filter { it.parentPid != null }
            .groupBy { it.parentPid!!.value }

        val outputPaths = outputs.mapNotNull { it.exePath.toNioPathOrNull()?.pathWithoutExeOrDllExtension }

        val visited = mutableSetOf(cliProcessPid)
        val queue = ArrayDeque<Long>().apply { add(cliProcessPid) }
        while (queue.isNotEmpty()) {
            val children = childrenByParent[queue.removeFirst()] ?: continue
            for (child in children) {
                if (!visited.add(child.pid.value)) continue
                if (isAppHostProcess(child, outputPaths)) return child
                queue.add(child.pid.value)
            }
        }

        return null
    }

    private suspend fun isAppHostProcess(process: EelProcessInfo, outputPaths: List<Path>): Boolean {
        val executable = process.executable ?: return false
        if (executable.toNioPathOrNull()?.normalize()?.pathWithoutExeOrDllExtension in outputPaths) return true

        // `dotnet <assembly>` / `dotnet exec ... <assembly>`. Arguments are fetched lazily, so only `dotnet`
        // processes are inspected.
        if (PathUtil.getFileName(executable).lowercase() !in DOTNET_EXECUTABLE_NAMES) return false
        return process.arguments.await().any { it.toNioPathOrNull()?.pathWithoutExeOrDllExtension?.normalize() in outputPaths }
    }
}
