package com.jetbrains.aspire.rider.sessions

import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.sessions.DotNetSessionLaunchConfiguration
import com.jetbrains.rd.util.lifetime.Lifetime
import java.nio.file.Path

interface DotNetSessionProcessLauncherExtension {
    companion object {
        val EP_NAME =
            ExtensionPointName<DotNetSessionProcessLauncherExtension>("com.jetbrains.aspire.dotnetSessionProcessLauncherExtension")

        suspend fun getApplicableLauncher(projectPath: Path, project: Project): DotNetSessionProcessLauncherExtension? {
            for (launcher in EP_NAME.extensionList.sortedBy { it.priority }) {
                if (launcher.isApplicable(projectPath, project))
                    return launcher
            }
            return null
        }
    }

    val priority: Int

    suspend fun isApplicable(projectPath: Path, project: Project): Boolean

    suspend fun launchRunProcess(
        sessionId: String,
        launchConfiguration: DotNetSessionLaunchConfiguration,
        sessionProcessEventListener: ProcessListener,
        sessionProcessLifetime: Lifetime,
        aspireHostRunConfigName: String?,
        project: Project
    )

    suspend fun launchDebugProcess(
        sessionId: String,
        launchConfiguration: DotNetSessionLaunchConfiguration,
        sessionProcessEventListener: ProcessListener,
        sessionProcessLifetime: Lifetime,
        aspireHostRunConfigName: String?,
        project: Project
    )
}