package com.jetbrains.aspire.rider.processLaunchers

import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.lifetime.Lifetime
import java.nio.file.Path

internal interface DotNetSessionProcessLauncherExtension {
    companion object {
        private val EP_NAME =
            ExtensionPointName<DotNetSessionProcessLauncherExtension>("com.jetbrains.aspire.dotnetSessionProcessLauncherExtension")

        suspend fun getApplicableLauncher(projectPath: Path, project: Project): DotNetSessionProcessLauncherExtension? {
            for (launcher in EP_NAME.extensionList.sortedBy { it.priority }) {
                if (launcher.isApplicable(projectPath, project))
                    return launcher
            }
            return null
        }

        internal fun <T : DotNetSessionProcessLauncherExtension> getLauncher(launcherClass: Class<T>): T =
            EP_NAME.findExtensionOrFail(launcherClass)
    }

    val priority: Int

    suspend fun isApplicable(projectPath: Path, project: Project): Boolean

    suspend fun launchRunProcess(
        sessionId: String,
        launchConfiguration: SessionProcessLaunchConfiguration,
        sessionProcessEventListener: ProcessListener,
        sessionProcessLifetime: Lifetime,
        appHostFile: Path,
        project: Project
    )

    suspend fun launchDebugProcess(
        sessionId: String,
        launchConfiguration: SessionProcessLaunchConfiguration,
        sessionProcessEventListener: ProcessListener,
        sessionProcessLifetime: Lifetime,
        appHostFile: Path,
        project: Project
    )
}
