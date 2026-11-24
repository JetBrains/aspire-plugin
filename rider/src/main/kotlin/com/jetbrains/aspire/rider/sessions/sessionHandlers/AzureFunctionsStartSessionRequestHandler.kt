package com.jetbrains.aspire.rider.sessions.sessionHandlers

import com.intellij.openapi.project.Project
import com.jetbrains.aspire.rider.processLaunchers.DotNetSessionProcessLauncherExtension
import com.jetbrains.aspire.rider.processLaunchers.SessionProcessLaunchConfiguration
import com.jetbrains.aspire.rider.processLaunchers.azureFunctions.AzureFunctionsSessionProcessLauncher
import com.jetbrains.aspire.sessions.AzureFunctionsSessionLaunchConfiguration
import com.jetbrains.aspire.sessions.StartSessionRequest
import java.nio.file.Path

/**
 *  Handles Aspire session requests with the `azure-functions` launch configuration.
 *
 *  Every project is launched with [AzureFunctionsSessionProcessLauncher], whether it's part of the solution.
 */
internal class AzureFunctionsStartSessionRequestHandler : AbstractDotNetStartSessionRequestHandler() {
    override val sessionType: String = "azure-functions"

    override fun isApplicable(request: StartSessionRequest) =
        request.launchConfiguration is AzureFunctionsSessionLaunchConfiguration

    override fun toProcessLaunchConfiguration(request: StartSessionRequest): SessionProcessLaunchConfiguration? {
        val launchConfiguration = request.launchConfiguration as? AzureFunctionsSessionLaunchConfiguration
            ?: return null

        return SessionProcessLaunchConfiguration(
            projectPath = launchConfiguration.projectPath,
            launchProfile = null,
            disableLaunchProfile = true,
            args = launchConfiguration.args,
            envs = launchConfiguration.envs
        )
    }

    override suspend fun findLauncher(projectPath: Path, project: Project): DotNetSessionProcessLauncherExtension =
        DotNetSessionProcessLauncherExtension.getLauncher(AzureFunctionsSessionProcessLauncher::class.java)
}
