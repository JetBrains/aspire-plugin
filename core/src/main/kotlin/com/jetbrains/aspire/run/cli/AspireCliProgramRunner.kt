package com.jetbrains.aspire.run.cli

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.runners.AsyncProgramRunner
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.showRunContent
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.application.EDT
import com.jetbrains.aspire.AspireService
import com.jetbrains.aspire.common.AsyncRunProfileState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.toPromiseWithoutLogError

internal class AspireCliProgramRunner : AsyncProgramRunner<RunnerSettings>() {
    override fun getRunnerId(): String = "AspireCliProgramRunner"

    override fun canRun(executorId: String, profile: RunProfile): Boolean = profile is AspireCliRunConfiguration

    override fun execute(environment: ExecutionEnvironment, state: RunProfileState): Promise<RunContentDescriptor?> {
        // async(Dispatchers.EDT) ensures the Deferred completes on EDT, so that
        // toPromise's invokeOnCompletion resolves the promise on EDT in the same
        // dispatch cycle as session creation.
        return AspireService.getInstance(environment.project).scope
            .async(Dispatchers.EDT) {
                val executionResult = withContext(Dispatchers.IO) {
                    require(state is AsyncRunProfileState)
                    state.executeSuspending(environment.executor, this@AspireCliProgramRunner)
                }
                showRunContent(executionResult, environment)
            }
            .toPromiseWithoutLogError()
    }
}
