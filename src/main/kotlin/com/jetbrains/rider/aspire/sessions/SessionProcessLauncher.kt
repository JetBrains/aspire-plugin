@file:Suppress("LoggingSimilarMessage")

package com.jetbrains.rider.aspire.sessions

import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.isNotAlive
import com.jetbrains.rider.aspire.generated.CreateSessionRequest
import com.jetbrains.rider.aspire.sessions.projectLaunchers.SessionProcessLauncherExtension

@Service(Service.Level.PROJECT)
class SessionProcessLauncher(private val project: Project) {
    companion object {
        fun getInstance(project: Project) = project.service<SessionProcessLauncher>()

        private val LOG = logger<SessionProcessLauncher>()
    }

    suspend fun launchSessionProcess(
        sessionId: String,
        sessionModel: CreateSessionRequest,
        sessionProcessEventListener: ProcessListener,
        sessionProcessLifetime: Lifetime,
        aspireHostRunConfigName: String?,
    ) {
        LOG.info("Starting a session process for the project ${sessionModel.projectPath}")

        if (sessionProcessLifetime.isNotAlive) {
            LOG.warn("Unable to run project ${sessionModel.projectPath} because lifetimes are not alive")
            return
        }

        if (sessionModel.debug) {
            launchDebugProcess(
                sessionId,
                sessionModel,
                sessionProcessEventListener,
                sessionProcessLifetime,
                aspireHostRunConfigName
            )
        } else {
            launchRunProcess(
                sessionId,
                sessionModel,
                sessionProcessEventListener,
                sessionProcessLifetime,
                aspireHostRunConfigName
            )
        }
    }

    private suspend fun launchDebugProcess(
        sessionId: String,
        sessionModel: CreateSessionRequest,
        sessionProcessEventListener: ProcessListener,
        sessionProcessLifetime: Lifetime,
        aspireHostRunConfigName: String?
    ) {
        val processLauncher = getSessionProcessLauncher(sessionModel.projectPath)
        if (processLauncher == null) {
            LOG.warn("Unable to find appropriate process launcher for the project ${sessionModel.projectPath}")
            return
        }

        processLauncher.launchDebugProcess(
            sessionId,
            sessionModel,
            sessionProcessEventListener,
            sessionProcessLifetime,
            aspireHostRunConfigName,
            project,
        )
    }

    private suspend fun launchRunProcess(
        sessionId: String,
        sessionModel: CreateSessionRequest,
        sessionProcessEventListener: ProcessListener,
        sessionProcessLifetime: Lifetime,
        aspireHostRunConfigName: String?
    ) {
        val processLauncher = getSessionProcessLauncher(sessionModel.projectPath)
        if (processLauncher == null) {
            LOG.warn("Unable to find appropriate process launcher for the project ${sessionModel.projectPath}")
            return
        }

        processLauncher.launchRunProcess(
            sessionId,
            sessionModel,
            sessionProcessEventListener,
            sessionProcessLifetime,
            aspireHostRunConfigName,
            project,
        )
    }

    private suspend fun getSessionProcessLauncher(projectPath: String): SessionProcessLauncherExtension? {
        for (launcher in SessionProcessLauncherExtension.EP_NAME.extensionList.sortedBy { it.priority }) {
            if (launcher.isApplicable(projectPath, project))
                return launcher
        }

        return null
    }
}