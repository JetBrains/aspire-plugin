package com.jetbrains.aspire.extensions

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import java.nio.file.Path

/**
 * The line in an AppHost's entry file that its `DistributedApplication.CreateBuilder(...)` call starts on.
 *
 * [createBuilderLine] is zero-based, matching [com.intellij.xdebugger.breakpoints.XLineBreakpoint.getLine].
 */
data class AppHostEntryPoint(val filePath: Path, val createBuilderLine: Int)

/**
 * Resolves where an AppHost's `DistributedApplication.CreateBuilder(...)` call is.
 *
 * The Aspire CLI runner cannot launch the AppHost itself, so it debugs it by setting
 * `ASPIRE_WAIT_FOR_DEBUGGER=true` and attaching while Aspire spins inside `CreateBuilder` (see
 * [AppHostDebuggerExtension]). Everything at or above that line has therefore already run by the time the
 * debugger attaches, and [com.jetbrains.aspire.run.cli.AspireCliUnreachableBreakpointService] uses this
 * location to tell the user that breakpoints there cannot be hit.
 *
 * Dispatch mirrors [AppHostDebuggerExtension]: the applicable implementation is chosen by [isApplicable]
 * (lowest [priority] first). The AppHost can be written in different languages, so finding the call requires
 * language-specific parsing and lives in the implementation (e.g. the C# implementation in the `rider`
 * module, which resolves it on the ReSharper backend via PSI).
 */
interface AppHostEntryPointLocator {
    companion object {
        private val EP_NAME =
            ExtensionPointName<AppHostEntryPointLocator>("com.jetbrains.aspire.appHostEntryPointLocator")

        /**
         * Resolves the applicable locator for [appHostFilePath], preferring lower [priority] values,
         * or `null` when no registered extension handles this AppHost language.
         */
        fun findApplicable(appHostFilePath: Path): AppHostEntryPointLocator? =
            EP_NAME.extensionList.sortedBy { it.priority }.firstOrNull { it.isApplicable(appHostFilePath) }
    }

    val priority: Int

    fun isApplicable(appHostFilePath: Path): Boolean

    /**
     * Finds the entry point of the AppHost described by [appHostFilePath] (a project file or a single-file
     * AppHost), or returns `null` when it cannot be resolved — for instance while the solution is still
     * loading, or when the AppHost has no recognizable `CreateBuilder` call.
     *
     * Implementations bound their wait so a never-loading backend does not hang the caller.
     */
    suspend fun findEntryPoint(appHostFilePath: Path, project: Project): AppHostEntryPoint?
}
