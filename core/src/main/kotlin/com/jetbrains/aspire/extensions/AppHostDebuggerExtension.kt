package com.jetbrains.aspire.extensions

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import java.nio.file.Path

/**
 * Attaches a debugger to the AppHost process launched by `aspire run` in the CLI runner.
 *
 * The `aspire run` CLI launches the AppHost as a plain child process, so the IDE cannot debug it through
 * the DCP child-session path (which only ever carries child resources). Instead, the CLI runner starts the
 * AppHost with `ASPIRE_WAIT_FOR_DEBUGGER=true`, which makes Aspire's `DistributedApplication.CreateBuilder`
 * block at the very first line of the AppHost and spin until a debugger attaches. An implementation locates
 * the AppHost process among the descendants of the `aspire run` CLI process ([cliProcessPid]) and attaches
 * the matching debugger while the AppHost is held there.
 *
 * Dispatch mirrors [StartSessionRequestHandler]: the applicable implementation is chosen by
 * [isApplicable] (lowest [priority] first). The AppHost can be written in different languages, so the
 * "what an AppHost process looks like" and "how to attach" logic is language-specific and lives in the
 * implementation (e.g. the C# implementation in the `rider` module).
 */
interface AppHostDebuggerExtension {
    companion object {
        private val EP_NAME =
            ExtensionPointName<AppHostDebuggerExtension>("com.jetbrains.aspire.appHostDebugger")

        /**
         * Resolves the applicable debugger for [appHostFilePath], preferring lower [priority] values,
         * or `null` when no registered extension handles this AppHost language.
         */
        fun findApplicable(appHostFilePath: Path): AppHostDebuggerExtension? =
            EP_NAME.extensionList.sortedBy { it.priority }.firstOrNull { it.isApplicable(appHostFilePath) }
    }

    val priority: Int

    fun isApplicable(appHostFilePath: Path): Boolean

    /**
     * Locates the AppHost process among the descendants of the `aspire run` process [cliProcessPid] and
     * attaches a debugger to it. Suspends until the attach completes (or the enclosing scope is cancelled,
     * e.g. when the run is stopped); implementations bound the wait so a missing process does not hang.
     */
    suspend fun attachToAppHost(appHostFilePath: Path, cliProcessPid: Long, project: Project)
}
