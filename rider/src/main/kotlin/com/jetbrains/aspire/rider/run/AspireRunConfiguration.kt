package com.jetbrains.aspire.rider.run

import com.intellij.execution.configurations.RunConfiguration
import com.jetbrains.rider.debugger.IRiderDebuggable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface AspireRunConfiguration : IRiderDebuggable, RunConfiguration {
    val parameters: AspireRunConfigurationParameters
}