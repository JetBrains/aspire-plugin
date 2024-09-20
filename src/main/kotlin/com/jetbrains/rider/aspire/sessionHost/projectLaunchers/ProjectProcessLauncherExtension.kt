package com.jetbrains.rider.aspire.sessionHost.projectLaunchers

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.aspire.generated.SessionModel
import com.jetbrains.rider.aspire.sessionHost.SessionEvent
import kotlinx.coroutines.flow.MutableSharedFlow

interface ProjectProcessLauncherExtension {
    companion object {
        val EP_NAME =
            ExtensionPointName<ProjectProcessLauncherExtension>("com.jetbrains.rider.aspire.projectProcessLauncherExtension")
    }

    val priority: Int

    suspend fun isApplicable(projectPath: String, project: Project): Boolean

    suspend fun launchRunProcess(
        sessionId: String,
        sessionModel: SessionModel,
        sessionLifetime: Lifetime,
        sessionEvents: MutableSharedFlow<SessionEvent>,
        project: Project
    )

    suspend fun launchDebugProcess(
        sessionId: String,
        sessionModel: SessionModel,
        sessionLifetime: Lifetime,
        sessionEvents: MutableSharedFlow<SessionEvent>,
        project: Project
    )
}