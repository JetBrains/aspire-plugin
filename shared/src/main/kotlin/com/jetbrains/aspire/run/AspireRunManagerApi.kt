package com.jetbrains.aspire.run

import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.util.application
import com.jetbrains.aspire.worker.AspireAppHostId
import org.jetbrains.annotations.ApiStatus

/** Manages running and stopping configurations associated with an AppHost. */
@ApiStatus.Internal
interface AspireRunManagerApi {
    companion object {
        suspend fun getInstance(): AspireRunManagerApi = application.serviceAsync()
    }

    /** Runs a configuration for the given AppHost using the Run or Debug executor, depending on [underDebug]. */
    suspend fun executeConfigurationForAppHost(project: Project, appHostId: AspireAppHostId, underDebug: Boolean)

    /** Stops all running configurations for the given AppHost. */
    suspend fun stopConfigurationForAppHost(project: Project, appHostId: AspireAppHostId)
}
