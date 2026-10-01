package com.jetbrains.aspire.run

import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/** Manages running and stopping configurations associated with an AppHost. */
@ApiStatus.Internal
interface AspireRunConfigurationManager {
    /** Remembers the configuration launched for the given AppHost. */
    fun saveRunConfigurationForAppHost(appHostFile: Path, runConfigurationName: String)

    /** Runs a configuration for the given AppHost using the Run or Debug executor, depending on [underDebug]. */
    fun executeConfigurationForAppHost(appHostFile: Path, underDebug: Boolean)

    /** Stops all running configurations for the given AppHost. */
    fun stopConfigurationForAppHost(appHostFile: Path)
}
