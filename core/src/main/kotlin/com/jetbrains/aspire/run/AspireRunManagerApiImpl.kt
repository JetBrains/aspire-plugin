package com.jetbrains.aspire.run

import com.intellij.openapi.project.Project
import com.jetbrains.aspire.worker.AspireAppHostId

internal class AspireRunManagerApiImpl : AspireRunManagerApi {
    override suspend fun executeConfigurationForAppHost(project: Project, appHostId: AspireAppHostId, underDebug: Boolean) {
        AspireRunConfigurationManager.getInstance(project).executeConfigurationForAppHost(appHostId, underDebug)
    }

    override suspend fun stopConfigurationForAppHost(project: Project, appHostId: AspireAppHostId) {
        AspireRunConfigurationManager.getInstance(project).stopConfigurationForAppHost(appHostId)
    }
}
