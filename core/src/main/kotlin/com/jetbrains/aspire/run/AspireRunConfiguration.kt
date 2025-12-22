package com.jetbrains.aspire.run

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface AspireRunConfiguration {
    val parameters: AspireRunConfigurationParameters
}