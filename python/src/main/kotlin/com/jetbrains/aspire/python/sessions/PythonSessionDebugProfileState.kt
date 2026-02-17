package com.jetbrains.aspire.python.sessions

import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.diagnostic.logger
import com.jetbrains.aspire.sessions.PythonSessionLaunchConfiguration
import com.jetbrains.python.debugger.PyDebugRunner
import com.jetbrains.python.run.PythonScriptCommandLineState
import com.jetbrains.rd.util.lifetime.Lifetime

class PythonSessionDebugProfileState(
    private val sessionId: String,
    private val launchConfiguration: PythonSessionLaunchConfiguration,
    private val environment: ExecutionEnvironment,
    private val sessionProcessEventListener: ProcessListener,
    private val sessionProcessLifetime: Lifetime,
    private val debugPort: Int
) : RunProfileState {
    companion object {
        private val LOG = logger<PythonSessionDebugProfileState>()
    }

    override fun execute(executor: Executor, runner: ProgramRunner<*>): ExecutionResult {
        val syntheticConfig = createSyntheticRunConfiguration(launchConfiguration, environment.project)
        val pythonState = PythonScriptCommandLineState(syntheticConfig, environment)

        val pyDebugRunner = PyDebugRunner()
        val patchers = pyDebugRunner.createCommandLinePatchers(
            environment.project, pythonState, syntheticConfig, debugPort
        )

        val executionResult = pythonState.execute(executor, *patchers)

        val processHandler = executionResult.processHandler

        sessionProcessLifetime.onTerminationIfAlive {
            if (!processHandler.isProcessTerminating && !processHandler.isProcessTerminated) {
                LOG.trace("Killing debug session process handler (id: $sessionId)")
                processHandler.destroyProcess()
            }
        }

        processHandler.addProcessListener(sessionProcessEventListener)

        return executionResult
    }
}
