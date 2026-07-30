package com.jetbrains.aspire.rider.debugger

import com.intellij.execution.process.ProcessInfo
import com.intellij.execution.process.impl.ProcessListUtil
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.extensions.AppHostDebuggerExtension
import com.jetbrains.aspire.rider.AspireRiderBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.time.Duration.Companion.seconds

/**
 * Attaches the `.NET Debugger` to a .NET AppHost launched by `aspire run` in the CLI runner.
 *
 * `AspireCliRunProfileState` sets `ASPIRE_WAIT_FOR_DEBUGGER=true`, so `DistributedApplication.CreateBuilder`
 * blocks at the very first line of the AppHost and spins until a debugger attaches. While the AppHost is
 * held there we find its process among the descendants of the `aspire run` CLI process and attach via
 * [AttachDebuggerService]. Because the AppHost blocks until attach, breakpoints anywhere in
 * `Program`/resource registration are hit.
 *
 * The PID is discovered from the OS process tree ([ProcessListUtil], the same reliable source
 * [AttachDebuggerService] already uses — WMI on Windows, `ps` on macOS/Linux), not from process output.
 * Attach is local-only; the CLI runner only dispatches here for local runs.
 */
internal class DotNetAppHostDebugger : AppHostDebuggerExtension {
    companion object {
        private val LOG = logger<DotNetAppHostDebugger>()

        // Bound the search a little above the AppHost-side ASPIRE_DEBUGGER_TIMEOUT the runner sets (120s):
        // keep looking while the AppHost is still blocked, but give up if it never appears.
        private val DISCOVERY_TIMEOUT = 150.seconds
        private val POLL_INTERVAL = 1.seconds

        // Roslyn compiler-server / MSBuild worker executables that appear in the build pipeline. They never
        // run the AppHost, so exclude them even if their command line mentions the AppHost project.
        private val HELPER_EXECUTABLE_TOKENS = listOf("vbcscompiler", "msbuild")

        // `dotnet <verb> ...` launchers (the build/run/watch pipeline). The real AppHost runs the built
        // assembly (`... <name>.dll`) or is the native apphost executable, never a bare `dotnet <verb>`.
        private val DOTNET_LAUNCHER_VERBS = setOf("run", "watch", "build", "msbuild", "restore", "test", "pack", "publish")
    }

    override val priority: Int = 100

    override fun isApplicable(appHostFilePath: Path): Boolean =
        when (appHostFilePath.extension.lowercase()) {
            "csproj", "cs" -> true // C# project or single-file (apphost.cs) AppHost
            else -> false
        }

    override suspend fun attachToAppHost(appHostFilePath: Path, cliProcessPid: Long, project: Project) {
        // The AppHost assembly/executable is named after the project file (e.g. `MyApp.AppHost`).
        val appHostBaseName = appHostFilePath.nameWithoutExtension.lowercase()

        val pid = withTimeoutOrNull(DISCOVERY_TIMEOUT) {
            var found: Int? = null
            while (found == null) {
                found = withContext(Dispatchers.IO) { findAppHostPid(cliProcessPid, appHostBaseName) }
                if (found == null) delay(POLL_INTERVAL)
            }
            found
        }

        if (pid == null) {
            LOG.warn(
                "Could not find the .NET AppHost process for $appHostFilePath among the descendants of the " +
                    "aspire CLI process $cliProcessPid within $DISCOVERY_TIMEOUT; skipping debugger attach"
            )
            return
        }

        LOG.trace { "Attaching .NET debugger to AppHost process $pid ($appHostFilePath)" }
        AttachDebuggerService.getInstance(project)
            .attach(pid, AspireRiderBundle.message("progress.attach.debugger.to.apphost"))
    }

    /**
     * Returns the PID of the managed AppHost among the transitive descendants of [cliProcessPid], or `null`
     * when there is no single unambiguous match yet (so the caller keeps polling rather than attaching to
     * the wrong process).
     */
    private fun findAppHostPid(cliProcessPid: Long, appHostBaseName: String): Int? {
        val processes = ProcessListUtil.getProcessList()
        if (processes.isEmpty()) return null

        val childrenByParent = processes.groupBy { it.parentPid }

        val descendants = mutableListOf<ProcessInfo>()
        val queue = ArrayDeque<Int>()
        val visited = mutableSetOf<Int>()
        queue.add(cliProcessPid.toInt())
        while (queue.isNotEmpty()) {
            val parentPid = queue.removeFirst()
            if (!visited.add(parentPid)) continue
            val children = childrenByParent[parentPid] ?: continue
            for (child in children) {
                descendants.add(child)
                queue.add(child.pid)
            }
        }

        return descendants.filter { isManagedAppHost(it, appHostBaseName) }.singleOrNull()?.pid
    }

    private fun isManagedAppHost(process: ProcessInfo, appHostBaseName: String): Boolean {
        val executableName = process.executableName.lowercase()
        // Exclude the Roslyn compiler server / MSBuild workers of the build pipeline.
        if (HELPER_EXECUTABLE_TOKENS.any { executableName.contains(it) }) return false

        // Exclude the `dotnet run`/`watch`/`build`/... launcher (its first arg is a build/run verb).
        val firstArg = process.args.trim().split(Regex("\\s+")).firstOrNull()?.lowercase()
        if (firstArg != null && firstArg in DOTNET_LAUNCHER_VERBS) return false

        // Positive match: the built AppHost assembly on the command line, or the native apphost executable.
        val commandLine = process.commandLine.lowercase()
        val matchesAssembly = commandLine.contains("$appHostBaseName.dll")
        val matchesExecutable = executableName == appHostBaseName || executableName == "$appHostBaseName.exe"
        return matchesAssembly || matchesExecutable
    }
}
