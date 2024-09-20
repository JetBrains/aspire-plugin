package com.jetbrains.rider.aspire.run

import com.intellij.execution.CantRunException
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.showRunContent
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.util.application
import com.jetbrains.rd.framework.*
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rdclient.protocol.RdDispatcher
import com.jetbrains.rider.aspire.AspireService
import com.jetbrains.rider.aspire.generated.aspireSessionHostModel
import com.jetbrains.rider.aspire.services.AspireServiceManager
import com.jetbrains.rider.aspire.sessionHost.SessionHostManager
import com.jetbrains.rider.aspire.util.DEBUG_SESSION_PORT
import com.jetbrains.rider.aspire.util.DEBUG_SESSION_TOKEN
import com.jetbrains.rider.aspire.util.DOTNET_DASHBOARD_RESOURCESERVICE_APIKEY
import com.jetbrains.rider.aspire.util.DOTNET_RESOURCE_SERVICE_ENDPOINT_URL
import com.jetbrains.rider.debugger.DotNetRunnerBase
import com.jetbrains.rider.run.RiderAsyncProgramRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.io.path.Path

class AspireHostProgramRunner : RiderAsyncProgramRunner<RunnerSettings>(), DotNetRunnerBase {
    companion object {
        private const val RUNNER_ID = "aspire-runner"

        private val LOG = logger<AspireHostProgramRunner>()
    }

    override fun getRunnerId() = RUNNER_ID

    override fun canRun(executorId: String, runConfiguration: RunProfile) = runConfiguration is AspireHostConfiguration

    override suspend fun executeAsync(
        environment: ExecutionEnvironment,
        state: RunProfileState
    ): RunContentDescriptor? {
        LOG.info("Executing Aspire run profile state")

        val aspireHostLifetimeDefinition = AspireService.getInstance(environment.project).lifetime.createNested()
        val config = createConfig(environment, state, aspireHostLifetimeDefinition.lifetime)

        LOG.trace { "Aspire session host config: $config" }

        startProtocolAndSubscribe(
            environment.project,
            config,
            aspireHostLifetimeDefinition
        )

        val executionResult = state.execute(environment.executor, this)
        if (executionResult == null) {
            LOG.warn("Unable to start Aspire run profile state")
            return null
        }

        AspireServiceManager.getInstance(environment.project)
            .updateAspireHostService(config.aspireHostProjectPath, executionResult)

        val processHandler = executionResult.processHandler
        aspireHostLifetimeDefinition.onTermination {
            LOG.trace("Aspire host lifetime is terminated")
            if (!processHandler.isProcessTerminating && !processHandler.isProcessTerminated) {
                processHandler.destroyProcess()
            }
        }
        processHandler.addProcessListener(object : ProcessListener {
            override fun processTerminated(event: ProcessEvent) {
                LOG.trace("Aspire host process is terminated")
                aspireHostLifetimeDefinition.executeIfAlive {
                    application.invokeLater {
                        aspireHostLifetimeDefinition.terminate(true)
                    }
                }
            }
        })

        return showRunContent(executionResult, environment)
    }

    private fun createConfig(
        environment: ExecutionEnvironment,
        state: RunProfileState,
        aspireHostLifetime: Lifetime
    ): AspireHostConfig {
        val aspireHostProfileState = state as? AspireHostRunProfileState
            ?: throw CantRunException("Unable to execute RunProfileState: $state")
        val aspireHostConfiguration = (environment.runnerAndConfigurationSettings?.configuration as? AspireHostConfiguration)
            ?: throw CantRunException("Requested configuration is not an AspireHostConfiguration")

        val environmentVariables = aspireHostProfileState.environmentVariables
        val debugSessionToken = environmentVariables[DEBUG_SESSION_TOKEN]
        val debugSessionPort = environmentVariables[DEBUG_SESSION_PORT]
            ?.substringAfter(':')
            ?.toInt()
        if (debugSessionToken == null || debugSessionPort == null)
            throw CantRunException("Unable to find token or port")

        val resourceServiceEndpointUrl = environmentVariables[DOTNET_RESOURCE_SERVICE_ENDPOINT_URL]
        val resourceServiceApiKey = environmentVariables[DOTNET_DASHBOARD_RESOURCESERVICE_APIKEY]

        val debuggingMode = environment.executor.id == DefaultDebugExecutor.EXECUTOR_ID

        val parameters = aspireHostConfiguration.parameters
        val aspireHostProjectPath = Path(parameters.projectFilePath)
        val aspireHostProjectUrl = parameters.startBrowserParameters.url
        val browser = parameters.startBrowserParameters.browser

        return AspireHostConfig(
            aspireHostConfiguration.name,
            debugSessionToken,
            debugSessionPort,
            aspireHostProjectPath,
            aspireHostProjectUrl,
            debuggingMode,
            resourceServiceEndpointUrl,
            resourceServiceApiKey,
            aspireHostLifetime,
            browser
        )
    }

    private suspend fun startProtocolAndSubscribe(
        project: Project,
        config: AspireHostConfig,
        aspireHostLifetimeDefinition: LifetimeDefinition
    ) = withContext(Dispatchers.EDT) {
        val protocol = startProtocol(config.aspireHostLifetime)
        val sessionHostModel = protocol.aspireSessionHostModel

        AspireHostRunManager.getInstance(project)
            .saveRunConfiguration(config.aspireHostProjectPath, aspireHostLifetimeDefinition, config.name)

        AspireServiceManager.getInstance(project)
            .startAspireHostService(config, sessionHostModel)

        SessionHostManager.getInstance(project)
            .startSessionHost(config, protocol.wire.serverPort, sessionHostModel)
    }

    private suspend fun startProtocol(lifetime: Lifetime) = withContext(Dispatchers.EDT) {
        val dispatcher = RdDispatcher(lifetime)
        val wire = SocketWire.Server(lifetime, dispatcher, null)
        val protocol = Protocol(
            "AspireSessionHost::protocol",
            Serializers(),
            Identities(IdKind.Server),
            dispatcher,
            wire,
            lifetime
        )
        return@withContext protocol
    }
}