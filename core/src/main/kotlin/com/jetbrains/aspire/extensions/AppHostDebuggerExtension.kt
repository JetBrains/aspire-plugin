package com.jetbrains.aspire.extensions

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import java.nio.file.Path

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

    suspend fun attachToAppHost(appHostFilePath: Path, cliProcessPid: Long, project: Project, scope: CoroutineScope): Result<Unit>
}
