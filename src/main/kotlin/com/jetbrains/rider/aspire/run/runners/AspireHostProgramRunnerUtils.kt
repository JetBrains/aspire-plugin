package com.jetbrains.rider.aspire.run.runners

import com.intellij.execution.CantRunException
import com.intellij.execution.ExecutionResult
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.util.application
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rider.aspire.generated.AspireHostModelConfig
import com.jetbrains.rider.aspire.run.AspireHostConfiguration
import com.jetbrains.rider.aspire.run.AspireHostRunManager
import com.jetbrains.rider.aspire.run.states.*
import com.jetbrains.rider.aspire.sessionHost.SessionHostManager2
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

private val LOG = Logger.getInstance("#com.jetbrains.rider.aspire.run.runners.AspireHostProgramRunnerUtils")

fun setUpAspireHostModel(
    environment: ExecutionEnvironment,
    state: AspireHostProfileState,
    aspireHostProcessHandlerLifetime: Lifetime,
): AspireHostModelConfig {
    val aspireHostConfiguration =
        (environment.runnerAndConfigurationSettings?.configuration as? AspireHostConfiguration)
            ?: throw CantRunException("Requested configuration is not an AspireHostConfiguration")

    val isDebuggingMode = environment.executor.id == DefaultDebugExecutor.EXECUTOR_ID

    val dcpInstancePrefix = requireNotNull(state.getDcpInstancePrefix())
    val resourceServiceEndpointUrl = state.getResourceServiceEndpointUrl()
    val resourceServiceApiKey = state.getResourceServiceApiKey()

    val parameters = aspireHostConfiguration.parameters
    val aspireHostProjectPath = Path(parameters.projectFilePath)

    val browserToken = state.getDashboardBrowserToken()
    val aspireHostProjectUrl = if (browserToken != null) {
        "${parameters.startBrowserParameters.url}/login?t=$browserToken"
    } else {
        parameters.startBrowserParameters.url
    }

    val aspireHostConfig = AspireHostModelConfig(
        dcpInstancePrefix,
        aspireHostConfiguration.name,
        aspireHostProjectPath.absolutePathString(),
        resourceServiceEndpointUrl,
        resourceServiceApiKey,
        isDebuggingMode,
        aspireHostProjectUrl
    )

    val sessionHost = SessionHostManager2.getInstance(environment.project).sessionHost
    aspireHostProcessHandlerLifetime.bracketIfAlive({
        sessionHost.addAspireHostModel(aspireHostConfig)
    }, {
        sessionHost.removeAspireHostModel(aspireHostConfig)
    })

    return aspireHostConfig
}

fun saveRunConfiguration(
    project: Project,
    aspireHostProjectPath: Path,
    runConfigurationName: String,
    aspireHostLifetimeDefinition: LifetimeDefinition
) {
    AspireHostRunManager
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