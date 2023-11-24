package com.github.rafaelldi.aspireplugin.run

import com.intellij.execution.CantRunException
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.run.configurations.AsyncExecutorFactory

class AspireHostExecutorFactory(
    private val project: Project,
    private val parameters: AspireHostConfigurationParameters
) : AsyncExecutorFactory {
    override suspend fun create(
        executorId: String,
        environment: ExecutionEnvironment,
        lifetime: Lifetime
    ): RunProfileState = when (executorId) {
        DefaultDebugExecutor.EXECUTOR_ID -> throw CantRunException("")
        DefaultRunExecutor.EXECUTOR_ID -> throw CantRunException("")
        else -> throw CantRunException("")
    }
}