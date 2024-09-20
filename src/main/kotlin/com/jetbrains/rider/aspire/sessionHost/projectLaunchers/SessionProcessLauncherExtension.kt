package com.jetbrains.rider.aspire.sessionHost.projectLaunchers

import com.intellij.ide.browsers.WebBrowser
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.aspire.generated.SessionModel
import com.jetbrains.rider.aspire.sessionHost.SessionEvent
import kotlinx.coroutines.flow.MutableSharedFlow

interface SessionProcessLauncherExtension {
    companion object {
        val EP_NAME =
            ExtensionPointName<SessionProcessLauncherExtension>("com.jetbrains.rider.aspire.sessionProcessLauncherExtension")
    }

    val priority: Int

    suspend fun isApplicable(projectPath: String, project: Project): Boolean

    suspend fun launchRunProcess(
        sessionId: String,
        sessionModel: SessionModel,
        sessionProcessLifetime: Lifetime,
        sessionEvents: MutableSharedFlow<SessionEvent>,
        browser: WebBrowser?,
        project: Project,
        sessionProcessHandlerTerminated: (Int, String?) -> Unit
    )

    suspend fun launchDebugProcess(
        sessionId: String,
        sessionModel: SessionModel,
        sessionProcessLifetime: Lifetime,
        sessionEvents: MutableSharedFlow<SessionEvent>,
        browser: WebBrowser?,
        project: Project,
        sessionProcessHandlerTerminated: (Int, String?) -> Unit
    )
}