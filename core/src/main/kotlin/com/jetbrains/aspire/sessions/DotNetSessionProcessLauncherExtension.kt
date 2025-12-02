package com.jetbrains.aspire.sessions

import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.lifetime.Lifetime

interface DotNetSessionProcessLauncherExtension {
    companion object {
        val EP_NAME =
            ExtensionPointName<DotNetSessionProcessLauncherExtension>("com.jetbrains.aspire.dotnetSessionProcessLauncherExtension")
    }

    val priority: Int

    suspend fun isApplicable(projectPath: String, project: Project): Boolean

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