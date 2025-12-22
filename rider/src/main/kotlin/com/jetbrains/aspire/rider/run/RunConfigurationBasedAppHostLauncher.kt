package com.jetbrains.aspire.rider.run

import com.intellij.openapi.project.Project
import com.jetbrains.aspire.worker.AspireAppHost
import com.jetbrains.aspire.worker.AspireAppHostLauncher

internal class RunConfigurationBasedAppHostLauncher : AspireAppHostLauncher {
    override suspend fun launchAppHost(appHost: AspireAppHost, underDebug: Boolean, project: Project) {
        AspireRunConfigurationManager
            .getInstance(project)
            .executeConfigurationForHost(appHost, underDebug)
    }

    override suspend fun stopAppHost(appHost: AspireAppHost, project: Project) {
        AspireRunConfigurationManager
            .getInstance(project)
            .stopConfigurationForHost(appHost)
    }
}