package com.jetbrains.aspire.run.cli

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.runners.AsyncProgramRunner
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.showRunContent
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.application.EDT
import com.intellij.openapi.rd.util.lifetime
import com.intellij.openapi.rd.util.startWithBackgroundProgressAsync
import com.jetbrains.aspire.AspireCoreBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.toPromiseWithoutLogError

@ApiStatus.Internal
class AspireCliProgramRunner : AsyncProgramRunner<RunnerSettings>() {
    override fun getRunnerId(): String = "AspireCliProgramRunner"

    override fun canRun(executorId: String, profile: RunProfile): Boolean = profile is AspireCliRunConfiguration

    suspend fun executeSuspending(
        environment: ExecutionEnvironment,
        state: RunProfileState
    ): RunContentDescriptor? {
        val executionResult =
            if (state is AsyncRunProfileState) state.executeSuspending(environment.executor, this)
            else state.execute(environment.executor, this)

        return showRunContent(executionResult, environment)
    }

    override fun execute(
        environment: ExecutionEnvironment,
        state: RunProfileState
    ): Promise<RunContentDescriptor?> {
        val configurationName = environment.runnerAndConfigurationSettings?.name?.let {
            AspireCoreBundle.message(
                "run.configuration.launch.message",
                it
            )
        } ?: AspireCoreBundle.message("run.configuration.generic.launch.message")

        return environment.project.lifetime.startWithBackgroundProgressAsync(
            environment.project, configurationName, true
        ) {
            withContext(Dispatchers.EDT) {
                executeSuspending(environment, state)
            }
        }.toPromiseWithoutLogError()
    }

}
