package com.jetbrains.aspire.run

internal interface AspireRunConfiguration {
    val configurationName: String
    val parameters: AspireRunConfigurationParameters
}