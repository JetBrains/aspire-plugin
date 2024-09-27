@file:Suppress("LoggingSimilarMessage")

package com.jetbrains.rider.aspire.sessionHost

import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.isNotAlive
import com.jetbrains.rider.aspire.generated.SessionModel
import com.jetbrains.rider.aspire.run.AspireHostConfiguration
import com.jetbrains.rider.aspire.sessionHost.projectLaunchers.SessionProcessLauncherExtension

@Service(Service.Level.PROJECT)
class SessionProcessLauncher(private val project: Project) {
    companion object {
        fun getInstance(project: Project) = project.service<SessionProcessLauncher>()

        private val LOG = logger<SessionProcessLauncher>()
    }

    suspend fun launchSessionProcess(
        sessionId: String,
        sessionModel: SessionModel,
        sessionProcessEventListener: ProcessListener,
        sessionProcessTerminatedListener: ProcessListener,
        sessionProcessLifetime: Lifetime,
        debuggingMode: Boolean,
        hostRunConfiguration: AspireHostConfiguration?,
    ) {
        LOG.info("Starting a session process for the project ${sessionModel.projectPath}")

        if (sessionProcessLifetime.isNotAlive) {
            LOG.warn("Unable to run project ${sessionModel.projectPath} because lifetimes are not alive")
            return
        }

        if (debuggingMode) {
            launchDebugProcess(
                sessionId,
                sessionModel,
                sessionProcessEventListener,
                sessionProcessTerminatedListener,
                sessionProcessLifetime,
                hostRunConfiguration
            )
        } else {
            launchRunProcess(
                sessionId,
                sessionModel,
                sessionProcessEventListener,
                sessionProcessTerminatedListener,
                sessionProcessLifetime,
                hostRunConfiguration
            )
        }
    }

    private suspend fun launchDebugProcess(
        sessionId: String,
        sessionModel: SessionModel,
        sessionProcessEventListener: ProcessListener,
        sessionProcessTerminatedListener: ProcessListener,
        sessionProcessLifetime: Lifetime,
        hostRunConfiguration: AspireHostConfiguration?
    ) {
        val processLauncher = getSessionProcessLauncher(sessionModel)
        if (processLauncher == null) {
            LOG.warn("Unable to find appropriate process launcher for the project ${sessionModel.projectPath}")
            return
        }

        processLauncher.launchDebugProcess(
            sessionId,
            sessionModel,
            sessionProcessEventListener,
            sessionProcessTerminatedListener,
            sessionProcessLifetime,
            hostRunConfiguration,
            project,
        )
    }

    private suspend fun launchRunProcess(
        sessionId: String,
        sessionModel: SessionModel,
        sessionProcessEventListener: ProcessListener,
        sessionProcessTerminatedListener: ProcessListener,
        sessionProcessLifetime: Lifetime,
        hostRunConfiguration: AspireHostConfiguration?
    ) {
        val processLauncher = getSessionProcessLauncher(sessionModel)
        if (processLauncher == null) {
            LOG.warn("Unable to find appropriate process launcher for the project ${sessionModel.projectPath}")
            return
        }

        processLauncher.launchRunProcess(
            sessionId,
            sessionModel,
            sessionProcessEventListener,
            sessionProcessTerminatedListener,
            sessionProcessLifetime,
            hostRunConfiguration,
            project,
        )
    }

    private suspend fun getSessionProcessLauncher(sessionModel: SessionModel): SessionProcessLauncherExtension? {
        for (launcher in SessionProcessLauncherExtension.EP_NAME.extensionList.sortedBy { it.priority }) {
            if (launcher.isApplicable(sessionModel.projectPath, project))
                return launcher
        }

        return null
    }
}