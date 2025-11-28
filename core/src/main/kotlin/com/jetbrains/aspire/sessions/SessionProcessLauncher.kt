@file:Suppress("LoggingSimilarMessage")

package com.jetbrains.aspire.sessions

import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.isNotAlive
import com.jetbrains.aspire.generated.CreateSessionRequest
import com.jetbrains.aspire.sessions.projectLaunchers.SessionProcessLauncherExtension

/**
 * Service responsible for launching session processes with different modes such as debug or run.
 * The class ensures that the appropriate process launcher is used based on the session request.
 */
@Service(Service.Level.PROJECT)
internal class SessionProcessLauncher(private val project: Project) {
    companion object {
        fun getInstance(project: Project) = project.service<SessionProcessLauncher>()

        private val LOG = logger<SessionProcessLauncher>()
    }

    /**
     * Launches a session process based on the specified session parameters and execution context.
     *
     * @param sessionId Unique identifier for the session that needs to be launched.
     * @param sessionModel The data model containing details of the session, such as the project path, debug flag, and additional arguments.
     * @param sessionProcessEventListener Listener to capture various events from the session process.
     * @param sessionProcessLifetime The lifetime management context for the session process; ensures proper cleanup when no longer active.
     * @param aspireHostRunConfigName Optional name for the run configuration in the Aspire host, if applicable.
     */
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

        val preferred = SessionLaunchPreferenceService.getInstance(project).getPreferredLaunchMode(sessionModel.projectPath)
        val shouldDebug = when (preferred) {
            SessionLaunchMode.DEBUG -> true
            SessionLaunchMode.RUN -> false
            null -> sessionModel.debug
        }

        if (shouldDebug) {
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