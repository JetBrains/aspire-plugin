package com.jetbrains.aspire.extensions

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
interface AppHostDebuggerExtension {
    companion object {
        private val EP_NAME =
            ExtensionPointName<AppHostDebuggerExtension>("com.jetbrains.aspire.appHostDebugger")

        fun findApplicable(appHostFilePath: Path): AppHostDebuggerExtension? =
            EP_NAME.extensionList.firstOrNull { it.isApplicable(appHostFilePath) }
    }

    fun isApplicable(appHostFilePath: Path): Boolean

    suspend fun attachToAppHost(appHostFilePath: Path, cliProcessPid: Long, project: Project): Result<Unit>
}
