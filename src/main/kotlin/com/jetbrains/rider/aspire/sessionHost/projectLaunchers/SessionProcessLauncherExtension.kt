package com.jetbrains.rider.aspire.sessionHost.projectLaunchers

import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.aspire.generated.CreateSessionRequest

interface SessionProcessLauncherExtension {
    companion object {
        val EP_NAME =
            ExtensionPointName<SessionProcessLauncherExtension>("com.jetbrains.rider.aspire.sessionProcessLauncherExtension")
    }

    val priority: Int

    suspend fun isApplicable(projectPath: String, project: Project): Boolean

    suspend fun launchRunProcess(
        sessionId: String,
        sessionModel: CreateSessionRequest,
        sessionProcessEventListener: ProcessListener,
        sessionProcessLifetime: Lifetime,
        aspireHostRunConfigName: String,
        project: Project
    )

    suspend fun launchDebugProcess(
        sessionId: String,
        sessionModel: CreateSessionRequest,
        sessionProcessEventListener: ProcessListener,
        sessionProcessLifetime: Lifetime,
        aspireHostRunConfigName: String,
        project: Project
    )
}