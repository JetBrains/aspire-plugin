package com.jetbrains.aspire.rider.debugger

import com.intellij.openapi.project.Project
import com.jetbrains.aspire.extensions.AppHostDebuggerExtension
import kotlinx.coroutines.CoroutineScope
import java.nio.file.Path
import kotlin.io.path.extension

internal class DotNetAppHostDebuggerExtension : AppHostDebuggerExtension {
    override fun isApplicable(appHostFilePath: Path): Boolean =
        when (appHostFilePath.extension.lowercase()) {
            "csproj", "cs" -> true
            else -> false
        }

    override suspend fun attachToAppHost(
        appHostFilePath: Path,
        cliProcessPid: Long,
        appHostRunScope: CoroutineScope,
        project: Project,
    ): Result<Unit> = DotNetAppHostDebuggerService
        .getInstance(project)
        .attachToAppHost(appHostFilePath, cliProcessPid, appHostRunScope)
}
