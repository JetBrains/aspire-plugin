package com.jetbrains.aspire.rider.run

import com.intellij.execution.CantRunException
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.util.getAspireDashboardOtlpEndpointUrl
import com.jetbrains.aspire.util.getAspireDashboardResourceServiceApiKey
import com.jetbrains.aspire.util.getAspireResourceServiceEndpointUrl
import com.jetbrains.aspire.worker.AppHostListener
import com.jetbrains.aspire.worker.AspireAppHost.AppHostEnvironment
import java.nio.file.Path
import kotlin.io.path.Path

internal fun setUpAspireHostEnvironment(
    environment: ExecutionEnvironment,
    environmentVariables: Map<String, String>,
) {
    val configuration = environment.runnerAndConfigurationSettings?.configuration
    val aspireRunConfiguration = (configuration as? AspireRiderRunConfiguration)
        ?: throw CantRunException("Requested configuration is not an AspireRunConfiguration")

    val parameters = aspireRunConfiguration.parameters
    val appHostFile = Path(parameters.appHostFile)

    setUpAspireHostEnvironment(
        appHostFile,
        environmentVariables,
        environment.project
    )
}

internal fun setUpAspireHostEnvironment(
    appHostFile: Path,
    environmentVariables: Map<String, String>,
    project: Project
) {
    val resourceServiceEndpointUrl = environmentVariables.getAspireResourceServiceEndpointUrl()
    val resourceServiceApiKey = environmentVariables.getAspireDashboardResourceServiceApiKey()
    val otlpEndpointUrl = environmentVariables.getAspireDashboardOtlpEndpointUrl()

    val appHostEnvironment = AppHostEnvironment(
        resourceServiceEndpointUrl,
        resourceServiceApiKey,
        otlpEndpointUrl
    )

    project.messageBus
        .syncPublisher(AppHostListener.TOPIC)
        .appHostStarting(appHostFile, appHostEnvironment)
}