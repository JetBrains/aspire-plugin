package com.jetbrains.aspire.rider.run

import com.intellij.execution.CantRunException
import com.intellij.execution.runners.ExecutionEnvironment
import com.jetbrains.aspire.rider.run.states.*
import com.jetbrains.aspire.worker.AppHostListener
import com.jetbrains.aspire.worker.AspireAppHost.AppHostEnvironment
import kotlin.io.path.Path

internal fun setUpAspireHostEnvironment(
    environment: ExecutionEnvironment,
    state: AspireHostProfileState,
) {
    val configuration = environment.runnerAndConfigurationSettings?.configuration
    val aspireRunConfiguration = (configuration as? AspireRiderRunConfiguration)
        ?: throw CantRunException("Requested configuration is not an AspireRunConfiguration")

    val resourceServiceEndpointUrl = state.getResourceServiceEndpointUrl()
    val resourceServiceApiKey = state.getResourceServiceApiKey()
    val otlpEndpointUrl = state.getOtlpEndpointUrl()

    val parameters = aspireRunConfiguration.parameters
    val appHostFile = Path(parameters.appHostFile)

    val browserToken = state.getDashboardBrowserToken()
    val aspireHostProjectUrl = if (browserToken != null) {
        "${parameters.startBrowserParameters.url}/login?t=$browserToken"
    } else {
        parameters.startBrowserParameters.url
    }

    val appHostEnvironment = AppHostEnvironment(
        resourceServiceEndpointUrl,
        resourceServiceApiKey,
        otlpEndpointUrl,
        aspireHostProjectUrl
    )

    environment.project.messageBus
        .syncPublisher(AppHostListener.TOPIC)
        .appHostStarting(appHostFile, appHostEnvironment)
}
