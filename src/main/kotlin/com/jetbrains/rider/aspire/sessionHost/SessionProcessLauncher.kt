@file:Suppress("LoggingSimilarMessage")

package com.jetbrains.rider.aspire.sessionHost

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.isNotAlive
import com.jetbrains.rider.aspire.generated.SessionModel
import com.jetbrains.rider.aspire.sessionHost.projectLaunchers.ProjectProcessLauncherExtension
import kotlinx.coroutines.flow.MutableSharedFlow

@Service(Service.Level.PROJECT)
class SessionProcessLauncher(private val project: Project) {
    companion object {
        fun getInstance(project: Project) = project.service<SessionProcessLauncher>()

        private val LOG = logger<SessionProcessLauncher>()
    }

    suspend fun launchSessionProcess(
        sessionId: String,
        sessionModel: SessionModel,
        sessionProcessLifetime: Lifetime,
        sessionEvents: MutableSharedFlow<SessionEvent>,
        debuggingMode: Boolean
    ) {
        LOG.info("Starting a session process for the project ${sessionModel.projectPath}")

        if (sessionProcessLifetime.isNotAlive) {
            LOG.warn("Unable to run project ${sessionModel.projectPath} because lifetimes are not alive")
            return
        }

        if (debuggingMode || sessionModel.debug) {
            launchDebugProcess(
                sessionId,
                sessionModel,
                sessionProcessLifetime,
                sessionEvents
            )
        } else {
            launchRunProcess(
                sessionId,
                sessionModel,
                sessionProcessLifetime,
                sessionEvents
            )
        }
    }

    private suspend fun launchDebugProcess(
        sessionId: String,
        sessionModel: SessionModel,
        sessionProcessLifetime: Lifetime,
        sessionEvents: MutableSharedFlow<SessionEvent>
    ) {
        val processLauncher = getProjectProcessLauncher(sessionModel)
        if (processLauncher == null) {
            LOG.warn("Unable to find appropriate process launcher for the project ${sessionModel.projectPath}")
            return
        }

        processLauncher.launchDebugProcess(
            sessionId,
            sessionModel,
            sessionProcessLifetime,
            sessionEvents,
            project
        )
    }

    private suspend fun launchRunProcess(
        sessionId: String,
        sessionModel: SessionModel,
        sessionLifetime: Lifetime,
        sessionEvents: MutableSharedFlow<SessionEvent>
    ) {
        val processLauncher = getProjectProcessLauncher(sessionModel)
        if (processLauncher == null) {
            LOG.warn("Unable to find appropriate process launcher for the project ${sessionModel.projectPath}")
            return
        }

        processLauncher.launchRunProcess(
            sessionId,
            sessionModel,
            sessionLifetime,
            sessionEvents,
            project
        )
    }

    private suspend fun getProjectProcessLauncher(sessionModel: SessionModel): ProjectProcessLauncherExtension? {
        for (launcher in ProjectProcessLauncherExtension.EP_NAME.extensionList.sortedBy { it.priority }) {
            if (launcher.isApplicable(sessionModel.projectPath, project))
                return launcher
        }

        return null
    }
}