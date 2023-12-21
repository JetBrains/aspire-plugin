package me.rafaelldi.aspire.run

import com.intellij.execution.CantRunException
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.showRunContent
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.rd.util.lifetime
import com.intellij.openapi.rd.util.startOnUiAsync
import com.jetbrains.rider.debugger.DotNetProgramRunner
import com.jetbrains.rider.run.DotNetProcessRunProfileState
import com.jetbrains.rider.util.NetUtils
import me.rafaelldi.aspire.sessionHost.AspireSessionHostConfig
import me.rafaelldi.aspire.sessionHost.AspireSessionHostRunner
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.asPromise

class AspireHostProgramRunner : DotNetProgramRunner() {
    companion object {
        const val DEBUG_SESSION_TOKEN = "DEBUG_SESSION_TOKEN"
        const val DEBUG_SESSION_PORT = "DEBUG_SESSION_PORT"
        const val ASPNETCORE_URLS = "ASPNETCORE_URLS"
        const val DOTNET_DASHBOARD_OTLP_ENDPOINT_URL = "DOTNET_DASHBOARD_OTLP_ENDPOINT_URL"
        private const val RUNNER_ID = "aspire-runner"

        private val LOG = logger<AspireHostProgramRunner>()
    }

    override fun getRunnerId() = RUNNER_ID

    override fun canRun(executorId: String, runConfiguration: RunProfile) = runConfiguration is AspireHostConfiguration

    override fun execute(environment: ExecutionEnvironment, state: RunProfileState): Promise<RunContentDescriptor?> {
        LOG.trace("Executing Aspire run profile state")

        val dotnetProcessState = state as? DotNetProcessRunProfileState
            ?: throw CantRunException("Unable to execute RunProfileState: $state")

        val environmentVariables = dotnetProcessState.dotNetExecutable.environmentVariables
        val debugSessionToken = environmentVariables[DEBUG_SESSION_TOKEN]
        val debugSessionPort = environmentVariables[DEBUG_SESSION_PORT]
            ?.substringAfter(':')
            ?.toInt()
        if (debugSessionToken == null || debugSessionPort == null)
            throw CantRunException("Unable to find token or port")
        LOG.trace("Found $DEBUG_SESSION_TOKEN $debugSessionToken and $DEBUG_SESSION_PORT $debugSessionPort")

        val dashboardUrl = environmentVariables[ASPNETCORE_URLS]
        LOG.trace("Found $ASPNETCORE_URLS $dashboardUrl")
        val otlpEndpointUrl = environmentVariables[DOTNET_DASHBOARD_OTLP_ENDPOINT_URL]
        LOG.trace("Found $DOTNET_DASHBOARD_OTLP_ENDPOINT_URL $otlpEndpointUrl")

        val runProfileName = environment.runProfile.name
        val isDebug = environment.executor.id == DefaultDebugExecutor.EXECUTOR_ID

        val sessionHostLifetime = environment.project.lifetime.createNested()

        val sessionHostRunner = AspireSessionHostRunner.getInstance()
        val openTelemetryPort = NetUtils.findFreePort(77800)
        val config = AspireSessionHostConfig(
            debugSessionToken,
            runProfileName,
            isDebug,
            debugSessionPort,
            openTelemetryPort,
            dashboardUrl,
            otlpEndpointUrl
        )
        LOG.trace("Aspire session host config: $config")

        val sessionHostPromise = sessionHostLifetime.startOnUiAsync {
            sessionHostRunner.runSessionHost(environment.project, config, sessionHostLifetime)
        }.asPromise()

        return sessionHostPromise.then {
            val executionResult = state.execute(environment.executor, this)

            val processHandler = executionResult.processHandler
            sessionHostLifetime.onTermination {
                if (!processHandler.isProcessTerminating && !processHandler.isProcessTerminated) {
                    processHandler.destroyProcess()
                }
            }
            processHandler.addProcessListener(object : ProcessListener {
                override fun processTerminated(event: ProcessEvent) {
                    sessionHostLifetime.executeIfAlive {
                        sessionHostLifetime.terminate(true)
                    }
                }
            })

            return@then showRunContent(executionResult, environment)
        }
    }
}