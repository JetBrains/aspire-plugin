@file:OptIn(ExperimentalCoroutinesApi::class)

package com.jetbrains.rider.aspire.run.states

import com.intellij.execution.runners.ExecutionEnvironment
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.debugger.DebuggerHelperHost
import com.jetbrains.rider.run.dotNetCore.DotNetCoreDebugProfile
import com.jetbrains.rider.runtime.DotNetExecutable
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntime
import kotlinx.coroutines.ExperimentalCoroutinesApi

class AspireHostDebugProfileState(
    dotNetExecutable: DotNetExecutable,
    dotNetRuntime: DotNetCoreRuntime,
    environment: ExecutionEnvironment
) : DotNetCoreDebugProfile(
    dotNetRuntime,
    dotNetExecutable,
    environment,
    dotNetRuntime.cliExePath
), AspireHostProfileState {

    override val environmentVariables: Map<String, String> = dotNetExecutable.environmentVariables

    override suspend fun createWorkerRunInfo(
        lifetime: Lifetime,
        helper: DebuggerHelperHost,
        port: Int
    ) = createWorkerRunInfoForLauncherInfo(
        consoleKind,
        port,
        getLauncherInfo(lifetime, helper),
        dotNetExecutable.executableType,
        dotNetExecutable.usePty
    )
}