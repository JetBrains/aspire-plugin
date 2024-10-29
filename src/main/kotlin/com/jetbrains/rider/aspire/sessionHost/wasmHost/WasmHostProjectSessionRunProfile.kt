package com.jetbrains.rider.aspire.sessionHost.wasmHost

import com.intellij.execution.Executor
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.aspire.sessionHost.projectLaunchers.ProjectSessionProfile
import com.jetbrains.rider.aspire.sessionHost.projectLaunchers.ProjectSessionRunProfileState
import com.jetbrains.rider.runtime.DotNetExecutable
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntime
import java.nio.file.Path

class WasmHostProjectSessionRunProfile(
    private val sessionId: String,
    projectName: String,
    dotnetExecutable: DotNetExecutable,
    private val dotnetRuntime: DotNetCoreRuntime,
    private val sessionProcessEventListener: ProcessListener,
    private val sessionProcessTerminatedListener: ProcessListener,
    private val sessionProcessLifetime: Lifetime,
    aspireHostProjectPath: Path?
) : ProjectSessionProfile(projectName, dotnetExecutable, aspireHostProjectPath) {
    override fun getState(
        executor: Executor,
        environment: ExecutionEnvironment
    ) = ProjectSessionRunProfileState(
        sessionId,
        dotnetExecutable,
        dotnetRuntime,
        environment,
        sessionProcessEventListener,
        sessionProcessTerminatedListener,
        sessionProcessLifetime
    )
}