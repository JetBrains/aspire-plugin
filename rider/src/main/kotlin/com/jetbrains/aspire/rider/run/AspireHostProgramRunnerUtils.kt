package com.jetbrains.aspire.rider.run

import com.intellij.execution.CantRunException
import com.intellij.execution.ExecutionResult
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.application
import com.jetbrains.aspire.rider.run.states.*
import com.jetbrains.aspire.worker.AppHostListener
import com.jetbrains.aspire.worker.AspireAppHost.AppHostEnvironment
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import kotlin.io.path.Path

private val LOG = Logger.getInstance("#com.jetbrains.aspire.run.runners.AspireHostProgramRunnerUtils")

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

fun connectExecutionHandlerAndLifetime(
    executionResult: ExecutionResult,
    lifetimeDefinition: LifetimeDefinition
) {
    val processHandler = executionResult.processHandler

    lifetimeDefinition.onTermination {
        LOG.trace("Aspire host lifetime is terminated")
        if (!processHandler.isProcessTerminating && !processHandler.isProcessTerminated) {
            processHandler.destroyProcess()
        }
    }
    processHandler.addProcessListener(object : ProcessListener {
        override fun processTerminated(event: ProcessEvent) {
            LOG.trace("Aspire host process is terminated")
            lifetimeDefinition.executeIfAlive {
                application.invokeLater {
                    lifetimeDefinition.terminate(true)
                }
            }
        }
    })
}
