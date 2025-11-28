package com.jetbrains.aspire.sessions.executableLibrary

import com.intellij.execution.Executor
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.aspire.sessions.projectLaunchers.DotNetExecutableSessionDebugProfileState
import com.jetbrains.aspire.sessions.projectLaunchers.ProjectSessionProfile
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
) : ProjectSessionProfile(sessionId, projectPath, dotnetExecutable, aspireHostProjectPath, true) {
    override fun getState(
        executor: Executor,
        environment: ExecutionEnvironment
    ) = DotNetExecutableSessionDebugProfileState(
        sessionId,
        dotnetExecutable,
        dotnetRuntime,
        environment,
        sessionProcessEventListener,
        sessionProcessLifetime
    )
}