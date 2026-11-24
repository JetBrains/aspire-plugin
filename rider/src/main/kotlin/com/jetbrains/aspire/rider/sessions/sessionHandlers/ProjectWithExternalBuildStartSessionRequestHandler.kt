package com.jetbrains.aspire.rider.sessions.sessionHandlers

import com.intellij.openapi.project.Project
import com.jetbrains.aspire.rider.processLaunchers.DotNetSessionProcessLauncherExtension
import com.jetbrains.aspire.rider.processLaunchers.ExternalBuildSettings
import com.jetbrains.aspire.rider.processLaunchers.SessionProcessLaunchConfiguration
import com.jetbrains.aspire.rider.processLaunchers.externalBuild.ExternalBuildSessionProcessLauncher
import com.jetbrains.aspire.sessions.ExternalBuildSessionLaunchConfiguration
import com.jetbrains.aspire.sessions.StartSessionRequest
import java.nio.file.Path

/**
 *  Handles Aspire session requests with the `project-with-external-build.v1` launch configuration.
 *
 *  The AppHost has already built the projects, so they aren't built again.
 *  Every project is launched with [ExternalBuildSessionProcessLauncher].
 */
internal class ProjectWithExternalBuildStartSessionRequestHandler : AbstractDotNetStartSessionRequestHandler() {
    override val sessionType: String = "project-with-external-build.v1"

    override val buildProjectsBeforeLaunch = false

    override fun isApplicable(request: StartSessionRequest) =
        request.launchConfiguration is ExternalBuildSessionLaunchConfiguration

    override fun toProcessLaunchConfiguration(request: StartSessionRequest): SessionProcessLaunchConfiguration? {
        val launchConfiguration = request.launchConfiguration as? ExternalBuildSessionLaunchConfiguration
            ?: return null

        val externalBuild = ExternalBuildSettings(
            configuration = launchConfiguration.buildConfiguration,
            environment = launchConfiguration.buildEnvironment.orEmpty(),
            workingDirectory = launchConfiguration.buildWorkingDirectory
        )

        return SessionProcessLaunchConfiguration(
            projectPath = launchConfiguration.projectPath,
            launchProfile = launchConfiguration.launchProfile,
            disableLaunchProfile = launchConfiguration.disableLaunchProfile,
            args = launchConfiguration.args,
            envs = launchConfiguration.envs,
            externalBuild = externalBuild
        )
    }

    override suspend fun findLauncher(projectPath: Path, project: Project): DotNetSessionProcessLauncherExtension =
        DotNetSessionProcessLauncherExtension.getLauncher(ExternalBuildSessionProcessLauncher::class.java)
}
