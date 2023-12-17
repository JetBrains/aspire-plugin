package me.rafaelldi.aspire.run

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.showRunContent
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.rd.util.lifetime
import com.intellij.openapi.rd.util.startOnUiAsync
import com.jetbrains.rider.debugger.DotNetProgramRunner
import com.jetbrains.rider.run.DotNetProcessRunProfileState
import me.rafaelldi.aspire.sessionHost.AspireHostConfig
import me.rafaelldi.aspire.sessionHost.AspireHostRunner
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.asPromise
import org.jetbrains.concurrency.resolvedPromise

class AspireHostProgramRunner : DotNetProgramRunner() {
    companion object {
        const val DEBUG_SESSION_TOKEN = "DEBUG_SESSION_TOKEN"
        const val DEBUG_SESSION_PORT = "DEBUG_SESSION_PORT"
        private const val RUNNER_ID = "aspire-runner"
    }

    override fun getRunnerId() = RUNNER_ID

    override fun canRun(executorId: String, runConfiguration: RunProfile) = runConfiguration is AspireHostConfiguration

    override fun execute(environment: ExecutionEnvironment, state: RunProfileState): Promise<RunContentDescriptor?> {
        val dotnetProcessState = state as? DotNetProcessRunProfileState ?: return resolvedPromise() //todo: exception?
        val token = dotnetProcessState.dotNetExecutable.environmentVariables[DEBUG_SESSION_TOKEN]
        val port = dotnetProcessState.dotNetExecutable.environmentVariables[DEBUG_SESSION_PORT]
            ?.substringAfter(':')
            ?.toInt()
        if (token == null || port == null) return resolvedPromise() //todo: exception?

        val runProfileName = environment.runProfile.name
        val isDebug = environment.executor.id == DefaultDebugExecutor.EXECUTOR_ID

        val hostLifetime = environment.project.lifetime.createNested()

        val sessionHost = AspireHostRunner.getInstance()
        val config = AspireHostConfig(
            token,
            runProfileName,
            isDebug,
            port
        )

        val hostPromise = hostLifetime.startOnUiAsync {
            sessionHost.runHost(environment.project, config, hostLifetime)
        }.asPromise()

        return hostPromise.then {
            val executionResult = state.execute(environment.executor, this)

            val processHandler = executionResult.processHandler
            hostLifetime.onTermination {
                if (!processHandler.isProcessTerminating && !processHandler.isProcessTerminated) {
                    processHandler.destroyProcess()
                }
            }
            processHandler.addProcessListener(object : ProcessListener {
                override fun processTerminated(event: ProcessEvent) {
                    hostLifetime.executeIfAlive {
                        hostLifetime.terminate(true)
                    }
                }
            })

            return@then showRunContent(executionResult, environment)
        }
    }
}