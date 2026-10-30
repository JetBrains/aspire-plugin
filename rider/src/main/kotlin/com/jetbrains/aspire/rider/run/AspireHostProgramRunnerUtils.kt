package com.jetbrains.aspire.rider.run

import com.intellij.execution.CantRunException
import com.intellij.execution.runners.ExecutionEnvironment
import com.jetbrains.aspire.run.AspireExecutionListener.Companion.APP_HOST_ENVIRONMENT_KEY
import com.jetbrains.aspire.run.AspireExecutionListener.Companion.APP_HOST_ID_KEY
import com.jetbrains.aspire.util.DCP_INSTANCE_ID_PREFIX
import com.jetbrains.aspire.util.getAspireDashboardOtlpEndpointUrl
import com.jetbrains.aspire.util.getAspireDashboardResourceServiceApiKey
import com.jetbrains.aspire.util.getAspireResourceServiceEndpointUrl
import com.jetbrains.aspire.worker.AspireAppHost.AppHostEnvironment
import com.jetbrains.aspire.worker.AspireAppHostId

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

    val appHostId = environmentVariables[DCP_INSTANCE_ID_PREFIX]
        ?.takeIf { it.isNotBlank() }
        ?.let(::AspireAppHostId)
    environment.putUserData(APP_HOST_ID_KEY, appHostId)
    environment.putUserData(APP_HOST_ENVIRONMENT_KEY, appHostEnvironment)
}