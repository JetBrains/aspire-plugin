package com.jetbrains.aspire.rider.run

import com.intellij.execution.CantRunException
import com.intellij.execution.runners.ExecutionEnvironment
import com.jetbrains.aspire.run.AspireExecutionListener.Companion.APP_HOST_ENVIRONMENT_KEY
import com.jetbrains.aspire.util.getAspireDashboardOtlpEndpointUrl
import com.jetbrains.aspire.util.getAspireDashboardResourceServiceApiKey
import com.jetbrains.aspire.util.getAspireResourceServiceEndpointUrl
import com.jetbrains.aspire.worker.AspireAppHost.AppHostEnvironment

internal fun setUpAspireHostEnvironment(
    environment: ExecutionEnvironment,
    environmentVariables: Map<String, String>,
) {
    val configuration = environment.runnerAndConfigurationSettings?.configuration
    if (configuration !is AspireRiderRunConfiguration) {
        throw CantRunException("Requested configuration is not an AspireRunConfiguration")
    }

    val resourceServiceEndpointUrl = environmentVariables.getAspireResourceServiceEndpointUrl()
    val resourceServiceApiKey = environmentVariables.getAspireDashboardResourceServiceApiKey()
    val otlpEndpointUrl = environmentVariables.getAspireDashboardOtlpEndpointUrl()

    val appHostEnvironment = AppHostEnvironment(
        resourceServiceEndpointUrl,
        resourceServiceApiKey,
        otlpEndpointUrl
    )

    environment.putUserData(APP_HOST_ENVIRONMENT_KEY, appHostEnvironment)
}