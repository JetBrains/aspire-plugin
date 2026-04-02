package com.jetbrains.aspire.rider.run

import com.jetbrains.rider.debugger.IRiderDebuggable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface AspireRunConfiguration: IRiderDebuggable {
    val parameters: AspireRunConfigurationParameters
}