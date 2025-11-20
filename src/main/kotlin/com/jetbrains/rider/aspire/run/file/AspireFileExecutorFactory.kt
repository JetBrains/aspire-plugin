package com.jetbrains.rider.aspire.run.file

import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.run.configurations.AsyncExecutorFactory

internal class AspireFileExecutorFactory : AsyncExecutorFactory {
    override suspend fun create(
        executorId: String,
        environment: ExecutionEnvironment,
        lifetime: Lifetime
    ): RunProfileState {
        TODO("Not yet implemented")
    }
}