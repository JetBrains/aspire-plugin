package com.jetbrains.rider.aspire.sessionHost.dotnetProject

import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.aspire.sessionHost.projectLaunchers.ProjectSessionDebugProfileState
import com.jetbrains.rider.debugger.IRiderDebuggable
import com.jetbrains.rider.runtime.DotNetExecutable
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntime
import icons.RiderIcons

class DotNetProjectSessionDebugProfile(
    private val sessionId: String,
    private val projectName: String,
    private val dotnetExecutable: DotNetExecutable,
    private val dotnetRuntime: DotNetCoreRuntime,
    private val sessionProcessEventListener: ProcessListener,
    private val sessionProcessTerminatedListener: ProcessListener,
    private val sessionProcessLifetime: Lifetime
) : RunProfile, IRiderDebuggable {
    override fun getName() = projectName

    override fun getIcon() = RiderIcons.RunConfigurations.DotNetProject

    override fun getState(
        executor: Executor,
        environment: ExecutionEnvironment
    ) = ProjectSessionDebugProfileState(
        sessionId,
        dotnetExecutable,
        dotnetRuntime,
        environment,
        sessionProcessEventListener,
        sessionProcessTerminatedListener,
        sessionProcessLifetime
    )
}