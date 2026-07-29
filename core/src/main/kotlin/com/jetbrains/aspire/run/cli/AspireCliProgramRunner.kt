package com.jetbrains.aspire.run.cli

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.GenericProgramRunner
import org.jetbrains.annotations.ApiStatus

/**
 * Runs an [AspireCliRunConfiguration] under the Debug executor.
 *
 * The `aspire run` orchestrator process itself is not debugged; enabling the Debug executor makes
 * [AspireCliRunProfileState] start the embedded DCP server so child sessions can be debugged by the
 * registered [com.jetbrains.aspire.extensions.StartSessionRequestHandler]s. The Run executor is handled by
 * the platform's default runner.
 */
@ApiStatus.Internal
class AspireCliProgramRunner : GenericProgramRunner<RunnerSettings>() {
    override fun getRunnerId(): String = "AspireCliProgramRunner"

    override fun canRun(executorId: String, profile: RunProfile): Boolean =
        executorId == DefaultDebugExecutor.EXECUTOR_ID && profile is AspireCliRunConfiguration
}
