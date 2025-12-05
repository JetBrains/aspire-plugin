package com.jetbrains.aspire.rider.sessions.executableLibrary

import com.intellij.execution.Executor
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.aspire.rider.sessions.projectLaunchers.DotNetSessionDebugProfileState
import com.jetbrains.aspire.rider.sessions.projectLaunchers.DotNetSessionProfile
import com.jetbrains.rider.runtime.DotNetExecutable
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntime
import java.nio.file.Path

internal class ExecutableLibrarySessionDebugProfile(
    sessionId: String,
    projectPath: Path,
    dotnetExecutable: DotNetExecutable,
    private val dotnetRuntime: DotNetCoreRuntime,
    private val sessionProcessEventListener: ProcessListener,
    private val sessionProcessLifetime: Lifetime,
    aspireHostProjectPath: Path?
) : DotNetSessionProfile(sessionId, projectPath, dotnetExecutable, aspireHostProjectPath, true) {
    override fun getState(
        executor: Executor,
        environment: ExecutionEnvironment
    ) = DotNetSessionDebugProfileState(
        sessionId,
        dotnetExecutable,
        dotnetRuntime,
        environment,
        sessionProcessEventListener,
        sessionProcessLifetime
    )
}