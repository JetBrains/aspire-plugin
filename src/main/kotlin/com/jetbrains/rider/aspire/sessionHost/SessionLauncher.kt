@file:Suppress("LoggingSimilarMessage")

package com.jetbrains.rider.aspire.sessionHost

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.isNotAlive
import com.jetbrains.rider.aspire.generated.SessionModel
import com.jetbrains.rider.aspire.sessionHost.projectLaunchers.DotNetProjectLauncher
import com.jetbrains.rider.aspire.sessionHost.projectLaunchers.ProjectLauncher
import com.jetbrains.rider.aspire.sessionHost.projectLaunchers.WasmHostProjectLauncher
import com.jetbrains.rider.nuget.PackageVersionResolution
import com.jetbrains.rider.nuget.RiderNuGetInstalledPackageCheckerHost
import kotlinx.coroutines.flow.MutableSharedFlow

@Service(Service.Level.PROJECT)
class SessionLauncher(private val project: Project) {
    companion object {
        fun getInstance(project: Project) = project.service<SessionLauncher>()

        private val LOG = logger<SessionLauncher>()

        private const val DEV_SERVER_NUGET = "Microsoft.AspNetCore.Components.WebAssembly.DevServer"
        private const val SERVER_NUGET = "Microsoft.AspNetCore.Components.WebAssembly.Server"
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
        projectLauncher.launchDebugSession(
            sessionId,
            sessionModel,
            sessionLifetime,
            sessionEvents
        )
    }

    private suspend fun launchRunSession(
        sessionId: String,
        sessionModel: SessionModel,
        sessionLifetime: Lifetime,
        sessionEvents: MutableSharedFlow<SessionEvent>
    ) {
        val projectLauncher = getProjectLauncher(sessionModel)
        projectLauncher.launchRunSession(
            sessionId,
            sessionModel,
            sessionLifetime,
            sessionEvents
        )
    }

    private suspend fun getProjectLauncher(sessionModel: SessionModel): ProjectLauncher {
        if (isBlazorWasm(sessionModel.projectPath))
            return WasmHostProjectLauncher.getInstance(project)

        return DotNetProjectLauncher.getInstance(project)
    }

    private suspend fun isBlazorWasm(projectPath: String): Boolean {
        val nugetChecker = RiderNuGetInstalledPackageCheckerHost.getInstance(project)
        return nugetChecker.isPackageInstalled(PackageVersionResolution.EXACT, projectPath, DEV_SERVER_NUGET) ||
                nugetChecker.isPackageInstalled(PackageVersionResolution.EXACT, projectPath, SERVER_NUGET)
    }
}