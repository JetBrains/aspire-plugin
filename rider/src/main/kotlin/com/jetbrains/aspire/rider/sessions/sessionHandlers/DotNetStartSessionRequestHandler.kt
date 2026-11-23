package com.jetbrains.aspire.rider.sessions.sessionHandlers

import com.intellij.openapi.project.Project
import com.jetbrains.aspire.rider.sessions.DotNetSessionProcessLauncherExtension
import com.jetbrains.aspire.rider.sessions.SessionProcessLaunchConfiguration
import com.jetbrains.aspire.sessions.DotNetSessionLaunchConfiguration
import com.jetbrains.aspire.sessions.StartSessionRequest
import java.nio.file.Path

/**
 *  Handles Aspire session requests with the `project` launch configuration.
 *
 *  The handler detects the type of each project and launches it
 *  by using a specific [com.jetbrains.aspire.rider.sessions.DotNetSessionProcessLauncherExtension].
 */
internal class DotNetStartSessionRequestHandler : AbstractDotNetStartSessionRequestHandler() {
    override val sessionType: String = "project"

    override fun isApplicable(request: StartSessionRequest) =
        request.launchConfiguration is DotNetSessionLaunchConfiguration

    override fun toProcessLaunchConfiguration(request: StartSessionRequest): SessionProcessLaunchConfiguration? {
        val launchConfiguration = request.launchConfiguration as? DotNetSessionLaunchConfiguration
            ?: return null

        return SessionProcessLaunchConfiguration(
            projectPath = launchConfiguration.projectPath,
            launchProfile = launchConfiguration.launchProfile,
            disableLaunchProfile = launchConfiguration.disableLaunchProfile,
            args = launchConfiguration.args,
            envs = launchConfiguration.envs
        )
    }

    override suspend fun findLauncher(projectPath: Path, project: Project) =
        DotNetSessionProcessLauncherExtension.getApplicableLauncher(projectPath, project)
}
