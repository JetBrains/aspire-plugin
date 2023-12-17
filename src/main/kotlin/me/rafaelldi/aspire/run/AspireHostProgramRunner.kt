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
import me.rafaelldi.aspire.sessionHost.AspireSessionHostConfig
import me.rafaelldi.aspire.sessionHost.AspireSessionHostRunner
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.asPromise

class AspireHostProgramRunner : DotNetProgramRunner() {
    companion object {
        const val DEBUG_SESSION_TOKEN = "DEBUG_SESSION_TOKEN"
        const val DEBUG_SESSION_PORT = "DEBUG_SESSION_PORT"
        private const val RUNNER_ID = "aspire-runner"

        private val LOG = logger<AspireHostProgramRunner>()
    }

    override fun getRunnerId() = RUNNER_ID

    override fun canRun(executorId: String, runConfiguration: RunProfile) = runConfiguration is AspireHostConfiguration

    override fun execute(environment: ExecutionEnvironment, state: RunProfileState): Promise<RunContentDescriptor?> {
        LOG.trace("Executing Aspire run profile state")

        val dotnetProcessState = state as? DotNetProcessRunProfileState
            ?: throw CantRunException("Unable to execute RunProfileState: $state")
        val token = dotnetProcessState.dotNetExecutable.environmentVariables[DEBUG_SESSION_TOKEN]
        val port = dotnetProcessState.dotNetExecutable.environmentVariables[DEBUG_SESSION_PORT]
            ?.substringAfter(':')
            ?.toInt()
        if (token == null || port == null)
            throw CantRunException("Unable to find token or port")
        LOG.trace("Found token $token and port $port")

        val runProfileName = environment.runProfile.name
        val isDebug = environment.executor.id == DefaultDebugExecutor.EXECUTOR_ID

        val sessionHostLifetime = environment.project.lifetime.createNested()

        val sessionHostRunner = AspireSessionHostRunner.getInstance()
        val config = AspireSessionHostConfig(
            token,
            runProfileName,
            isDebug,
            port
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