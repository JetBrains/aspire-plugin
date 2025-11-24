package com.jetbrains.rider.aspire.run.file

import com.intellij.execution.CantRunException
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.aspire.run.AspireExecutorFactory
import com.jetbrains.rider.aspire.run.states.AspireHostRunProfileState
import com.jetbrains.rider.runtime.DotNetExecutable
import com.jetbrains.rider.runtime.RiderDotNetActiveRuntimeHost
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntime

internal class AspireFileExecutorFactory(
    private val project: Project,
    private val parameters: AspireFileConfigurationParameters
) : AspireExecutorFactory(project, parameters) {
    override suspend fun create(
        executorId: String,
        environment: ExecutionEnvironment,
        lifetime: Lifetime
    ): RunProfileState {
        TODO("Not yet implemented")
    }
}