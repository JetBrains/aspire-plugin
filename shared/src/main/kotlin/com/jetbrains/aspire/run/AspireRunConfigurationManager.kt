package com.jetbrains.aspire.run

import com.jetbrains.aspire.worker.AspireAppHostId
import org.jetbrains.annotations.ApiStatus

/** Manages running and stopping configurations associated with an AppHost. */
@ApiStatus.Internal
interface AspireRunConfigurationManager {
    /** Remembers the configuration launched for the given AppHost. */
    fun saveRunConfigurationForAppHost(appHostId: AspireAppHostId, runConfigurationName: String)

    /** Returns the name of the last configuration launched for the given AppHost. */
    fun getRunConfigurationNameForAppHost(appHostId: AspireAppHostId): String?

    /** Runs a configuration for the given AppHost using the Run or Debug executor, depending on [underDebug]. */
    fun executeConfigurationForAppHost(appHostId: AspireAppHostId, underDebug: Boolean)

    /** Stops all running configurations for the given AppHost. */
    fun stopConfigurationForAppHost(appHostId: AspireAppHostId)
}
