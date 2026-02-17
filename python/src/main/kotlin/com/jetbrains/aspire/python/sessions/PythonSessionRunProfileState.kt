package com.jetbrains.aspire.python.sessions

import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.util.execution.ParametersListUtil
import com.jetbrains.aspire.sessions.PythonSessionLaunchConfiguration
import com.jetbrains.python.run.PythonConfigurationType
import com.jetbrains.python.run.PythonRunConfiguration
import com.jetbrains.python.run.PythonScriptCommandLineState
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.rd.util.lifetime.Lifetime
import kotlin.io.path.absolutePathString

class PythonSessionRunProfileState(
    private val sessionId: String,
    private val launchConfiguration: PythonSessionLaunchConfiguration,
    private val environment: ExecutionEnvironment,
    private val sessionProcessEventListener: ProcessListener,
    private val sessionProcessLifetime: Lifetime
) : RunProfileState {
    companion object {
        private val LOG = logger<PythonSessionRunProfileState>()
    }

    override fun execute(executor: Executor, runner: ProgramRunner<*>): ExecutionResult {
        val syntheticConfig = createSyntheticRunConfiguration(launchConfiguration, environment.project)
        val pythonState = PythonScriptCommandLineState(syntheticConfig, environment)

        val executionResult = pythonState.execute(executor)
            ?: throw com.intellij.execution.ExecutionException("Failed to start Python process")

        val processHandler = executionResult.processHandler

        sessionProcessLifetime.onTerminationIfAlive {
            if (!processHandler.isProcessTerminating && !processHandler.isProcessTerminated) {
                LOG.trace("Killing run session process handler (id: $sessionId)")
                processHandler.destroyProcess()
            }
        }

        processHandler.addProcessListener(sessionProcessEventListener)

        return executionResult
    }
}
