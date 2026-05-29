package com.jetbrains.aspire.rider.run

import com.intellij.execution.configurations.RunConfiguration
import com.jetbrains.rider.debugger.IRiderDebuggable
import com.jetbrains.rider.run.IRiderRunnable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface AspireRunConfiguration : IRiderRunnable, IRiderDebuggable, RunConfiguration {
    val parameters: AspireRunConfigurationParameters
}