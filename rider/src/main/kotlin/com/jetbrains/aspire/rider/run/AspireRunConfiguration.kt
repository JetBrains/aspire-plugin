package com.jetbrains.aspire.rider.run

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface AspireRunConfiguration {
    val parameters: AspireRunConfigurationParameters
}