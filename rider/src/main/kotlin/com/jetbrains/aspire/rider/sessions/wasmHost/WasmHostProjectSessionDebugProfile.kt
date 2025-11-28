package com.jetbrains.aspire.rider.sessions.wasmHost

import com.intellij.execution.Executor
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.ide.browsers.StartBrowserSettings
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.aspire.sessions.projectLaunchers.ProjectSessionProfile
import com.jetbrains.rider.runtime.DotNetExecutable
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntime
import java.nio.file.Path

internal class WasmHostProjectSessionDebugProfile(
    sessionId: String,
    projectPath: Path,
    dotnetExecutable: DotNetExecutable,
    private val dotnetRuntime: DotNetCoreRuntime,
    private val browserSettings: StartBrowserSettings?,
    private val sessionProcessEventListener: ProcessListener,
    private val sessionProcessLifetime: Lifetime,
    aspireHostProjectPath: Path?
) : ProjectSessionProfile(sessionId, projectPath, dotnetExecutable, aspireHostProjectPath, true) {
    override fun getState(
        executor: Executor,
        environment: ExecutionEnvironment
    ) = WasmHostProjectSessionDebugProfileState(
        sessionId,
        projectPath,
        dotnetExecutable,
        dotnetRuntime,
        environment,
        browserSettings,
        sessionProcessEventListener,
        sessionProcessLifetime
    )
}