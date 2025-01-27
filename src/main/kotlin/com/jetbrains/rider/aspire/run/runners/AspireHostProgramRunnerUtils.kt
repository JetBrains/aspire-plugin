package com.jetbrains.rider.aspire.run.runners

import com.intellij.execution.CantRunException
import com.intellij.execution.ExecutionResult
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.util.application
import com.jetbrains.rd.framework.*
import com.jetbrains.rd.protocol.IdeRootMarshallersProvider
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rdclient.protocol.RdDispatcher
import com.jetbrains.rider.aspire.generated.AspireHostModel
import com.jetbrains.rider.aspire.generated.AspireHostModelConfig
import com.jetbrains.rider.aspire.generated.aspireSessionHostModel
import com.jetbrains.rider.aspire.listeners.AspireSessionHostListener
import com.jetbrains.rider.aspire.run.AspireHostConfig
import com.jetbrains.rider.aspire.run.AspireHostConfiguration
import com.jetbrains.rider.aspire.run.AspireHostRunManager
import com.jetbrains.rider.aspire.run.states.*
import com.jetbrains.rider.aspire.sessionHost.SessionHostManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

private val LOG = Logger.getInstance("#com.jetbrains.rider.aspire.run.runners.AspireHostProgramRunnerUtils")

fun createAspireHostConfig(
    environment: ExecutionEnvironment,
    state: AspireHostProfileState,
    aspireHostLifetime: Lifetime
): AspireHostConfig {
    val aspireHostConfiguration =
        (environment.runnerAndConfigurationSettings?.configuration as? AspireHostConfiguration)
            ?: throw CantRunException("Requested configuration is not an AspireHostConfiguration")

    val isDebuggingMode = environment.executor.id == DefaultDebugExecutor.EXECUTOR_ID

    val debugSessionToken = state.getDebugSessionToken()
    val debugSessionPort = state.getDebugSessionPort()
    if (debugSessionToken == null || debugSessionPort == null)
        throw CantRunException("Unable to find token or port")

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

    val config =  AspireHostConfig(
        aspireHostConfiguration.name,
        debugSessionToken,
        debugSessionPort,
        isDebuggingMode,
        resourceServiceEndpointUrl,
        resourceServiceApiKey,
        aspireHostLifetime,
        aspireHostProjectPath,
        aspireHostProjectUrl,
        aspireHostConfiguration
    )

    environment.project.messageBus
        .syncPublisher(AspireSessionHostListener.TOPIC)
        .configCreated(config.aspireHostProjectPath, config)

    return config
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

suspend fun startSessionHostAndSubscribe(
    config: AspireHostConfig,
    project: Project
) = withContext(Dispatchers.EDT) {
    val protocol = startSessionHostProtocol(config.aspireHostLifetime)
    val sessionHostModel = protocol.aspireSessionHostModel

    val aspireHostConfig = AspireHostModelConfig(
        config.debugSessionToken,
        config.aspireHostProjectPath.absolutePathString(),
        config.resourceServiceEndpointUrl,
        config.resourceServiceApiKey
    )
    val aspireHostModel = AspireHostModel(aspireHostConfig)

    sessionHostModel.aspireHosts.put(config.debugSessionToken, aspireHostModel)

    project.messageBus
        .syncPublisher(AspireSessionHostListener.TOPIC)
        .aspireHostModelCreated(aspireHostModel, config.aspireHostLifetime)

    SessionHostManager
        .getInstance(project)
        .startSessionHost(config, protocol.wire.serverPort, sessionHostModel)
}

private suspend fun startSessionHostProtocol(lifetime: Lifetime) = withContext(Dispatchers.EDT) {
    val dispatcher = RdDispatcher(lifetime)
    val wire = SocketWire.Server(lifetime, dispatcher, null)
    val protocol = Protocol(
        "AspireSessionHost::protocol",
        Serializers(IdeRootMarshallersProvider),
        Identities(IdKind.Server),
        dispatcher,
        wire,
        lifetime
    )
    return@withContext protocol
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