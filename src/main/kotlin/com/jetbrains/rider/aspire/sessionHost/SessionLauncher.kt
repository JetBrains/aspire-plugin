@file:Suppress("LoggingSimilarMessage")

package com.jetbrains.rider.aspire.sessionHost

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.isNotAlive
import com.jetbrains.rider.aspire.generated.SessionModel
import com.jetbrains.rider.aspire.sessionHost.projectLaunchers.AspireProjectLauncherExtension
import kotlinx.coroutines.flow.MutableSharedFlow

@Service(Service.Level.PROJECT)
class SessionLauncher(private val project: Project) {
    companion object {
        fun getInstance(project: Project) = project.service<SessionLauncher>()

        private val LOG = logger<SessionLauncher>()
    }

    suspend fun launchSession(
        sessionId: String,
        sessionModel: SessionModel,
        sessionLifetime: Lifetime,
        sessionEvents: MutableSharedFlow<SessionEvent>,
        debuggingMode: Boolean
    ) {
        LOG.info("Starting a session for the project ${sessionModel.projectPath}")

        if (sessionLifetime.isNotAlive) {
            LOG.warn("Unable to run project ${sessionModel.projectPath} because lifetimes are not alive")
            return
        }

        if (debuggingMode || sessionModel.debug) {
            launchDebugSession(
                sessionId,
                sessionModel,
                sessionLifetime,
                sessionEvents
            )
        } else {
            launchRunSession(
                sessionId,
                sessionModel,
                sessionLifetime,
                sessionEvents
            )
        }
    }

    private suspend fun launchDebugSession(
        sessionId: String,
        sessionModel: SessionModel,
        sessionLifetime: Lifetime,
        sessionEvents: MutableSharedFlow<SessionEvent>
    ) {
        val projectLauncher = getProjectLauncher(sessionModel)
        if (projectLauncher == null) {
            LOG.warn("Unable to find appropriate launcher for the project ${sessionModel.projectPath}")
            return
        }

        projectLauncher.launchDebugSession(
            sessionId,
            sessionModel,
            sessionLifetime,
            sessionEvents,
            project
        )
    }

    private suspend fun launchRunSession(
        sessionId: String,
        sessionModel: SessionModel,
        sessionLifetime: Lifetime,
        sessionEvents: MutableSharedFlow<SessionEvent>
    ) {
        val projectLauncher = getProjectLauncher(sessionModel)
        if (projectLauncher == null) {
            LOG.warn("Unable to find appropriate launcher for the project ${sessionModel.projectPath}")
            return
        }

        projectLauncher.launchRunSession(
            sessionId,
            sessionModel,
            sessionLifetime,
            sessionEvents,
            project
        )
    }

    private suspend fun getProjectLauncher(sessionModel: SessionModel): AspireProjectLauncherExtension? {
        for (launcher in AspireProjectLauncherExtension.EP_NAME.extensionList.sortedBy { it.priority }) {
            if (launcher.isApplicable(sessionModel.projectPath, project))
                return launcher
        }

        return null
    }
}