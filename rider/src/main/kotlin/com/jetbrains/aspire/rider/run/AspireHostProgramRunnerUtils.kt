package com.jetbrains.aspire.rider.run

import com.intellij.execution.CantRunException
import com.intellij.execution.ExecutionResult
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.util.application
import com.jetbrains.aspire.rider.run.states.*
import com.jetbrains.aspire.worker.AppHostListener
import com.jetbrains.aspire.worker.AspireAppHost.AppHostEnvironment
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import java.nio.file.Path
import kotlin.io.path.Path

private val LOG = Logger.getInstance("#com.jetbrains.aspire.run.runners.AspireHostProgramRunnerUtils")

internal fun setUpAspireHostEnvironmentAndSaveRunConfig(
    environment: ExecutionEnvironment,
    state: AspireHostProfileState,
    aspireHostProcessHandlerLifetimeDef: LifetimeDefinition,
) {
    val appHostFilePath = setUpAspireHostEnvironment(environment, state)

    val runConfigName = environment.runProfile.name
    LOG.trace { "Saving Aspire Host run configuration $runConfigName" }

    saveRunConfiguration(
        environment.project,
        appHostFilePath,
        runConfigName,
        aspireHostProcessHandlerLifetimeDef
    )
}

private fun setUpAspireHostEnvironment(
    environment: ExecutionEnvironment,
    state: AspireHostProfileState,
): Path {
    val configuration = environment.runnerAndConfigurationSettings?.configuration
    val aspireRunConfiguration = (configuration as? AspireRiderRunConfiguration)
        ?: throw CantRunException("Requested configuration is not an AspireRunConfiguration")

    val resourceServiceEndpointUrl = state.getResourceServiceEndpointUrl()
    val resourceServiceApiKey = state.getResourceServiceApiKey()
    val otlpEndpointUrl = state.getOtlpEndpointUrl()

    val parameters = aspireRunConfiguration.parameters
    val appHostFilePath = Path(parameters.appHostFilePath)

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
        .appHostStarting(appHostFilePath, appHostEnvironment)

    return appHostFilePath
}

private fun saveRunConfiguration(
    project: Project,
    aspireHostProjectPath: Path,
    runConfigurationName: String,
    aspireHostLifetimeDefinition: LifetimeDefinition
) {
    AspireRunConfigurationManager
        .getInstance(project)
        .saveRunConfiguration(aspireHostProjectPath, aspireHostLifetimeDefinition, runConfigurationName)
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
