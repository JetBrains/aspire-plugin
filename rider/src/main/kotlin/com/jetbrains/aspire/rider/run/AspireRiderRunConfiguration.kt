package com.jetbrains.aspire.rider.run

import com.intellij.execution.configurations.RunConfiguration
import com.jetbrains.aspire.run.AspireRunConfiguration
import com.jetbrains.rider.debugger.IRiderDebuggable
import com.jetbrains.rider.run.IRiderRunnable
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
interface AspireRiderRunConfiguration : IRiderRunnable, IRiderDebuggable, RunConfiguration, AspireRunConfiguration {
    val parameters: AspireRiderRunConfigurationParameters

    override val appHostFile: Path
        get() = Path.of(parameters.appHostFile)
}
