package com.jetbrains.aspire.rider.sessions.azureFunctions

import com.intellij.execution.Executor
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.jetbrains.aspire.rider.sessions.projectLaunchers.DotNetSessionProfile
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.runtime.DotNetExecutable
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntime
import icons.ReSharperIcons
import java.nio.file.Path
import javax.swing.Icon

internal class AzureFunctionsSessionDebugProfile(
    sessionId: String,
    projectPath: Path,
    dotnetExecutable: DotNetExecutable,
    private val dotnetRuntime: DotNetCoreRuntime,
    private val sessionProcessEventListener: ProcessListener,
    private val sessionProcessLifetime: Lifetime,
    aspireHostProjectPath: Path?
) : DotNetSessionProfile(sessionId, projectPath, dotnetExecutable, aspireHostProjectPath, true) {

    override fun getIcon(): Icon = ReSharperIcons.AzureFrontend.FunctionAppRun

    override fun getState(
        executor: Executor,
        environment: ExecutionEnvironment
    ) = AzureFunctionsSessionDebugProfileState(
        sessionId,
        dotnetExecutable,
        dotnetRuntime,
        environment,
        sessionProcessEventListener,
        sessionProcessLifetime
    )
}