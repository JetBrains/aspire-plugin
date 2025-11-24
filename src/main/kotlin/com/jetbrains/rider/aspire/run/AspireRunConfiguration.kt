package com.jetbrains.rider.aspire.run

internal interface AspireRunConfiguration {
    val configurationName: String
    val parameters: AspireRunConfigurationParameters
}