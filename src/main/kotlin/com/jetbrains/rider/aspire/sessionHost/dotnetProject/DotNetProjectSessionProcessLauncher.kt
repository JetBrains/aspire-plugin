package com.jetbrains.rider.aspire.sessionHost.dotnetProject

import com.intellij.execution.process.ProcessListener
import com.intellij.ide.browsers.StartBrowserSettings
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.aspire.sessionHost.projectLaunchers.BaseProjectSessionProcessLauncher
import com.jetbrains.rider.runtime.DotNetExecutable
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntime
import java.nio.file.Path

class DotNetProjectSessionProcessLauncher : BaseProjectSessionProcessLauncher() {
    override val priority = 10

    override val hotReloadExtension = DotNetProjectHotReloadConfigurationExtension()

    override suspend fun isApplicable(projectPath: String, project: Project) = true

    override fun getRunProfile(
        sessionId: String,
        projectName: String,
        dotnetExecutable: DotNetExecutable,
        dotnetRuntime: DotNetCoreRuntime,
        sessionProcessEventListener: ProcessListener,
        sessionProcessTerminatedListener: ProcessListener,
        sessionProcessLifetime: Lifetime,
        aspireHostProjectPath: Path?
    ) = DotNetProjectSessionRunProfile(
        sessionId,
        projectName,
        dotnetExecutable,
        dotnetRuntime,
        sessionProcessEventListener,
        sessionProcessTerminatedListener,
        sessionProcessLifetime,
        aspireHostProjectPath
    )

    override fun getDebugProfile(
        sessionId: String,
        projectName: String,
        projectPath: Path,
        dotnetExecutable: DotNetExecutable,
        dotnetRuntime: DotNetCoreRuntime,
        browserSettings: StartBrowserSettings?,
        sessionProcessEventListener: ProcessListener,
        sessionProcessTerminatedListener: ProcessListener,
        sessionProcessLifetime: Lifetime,
        aspireHostProjectPath: Path?
    ) = DotNetProjectSessionDebugProfile(
        sessionId,
        projectName,
        dotnetExecutable,
        dotnetRuntime,
        sessionProcessEventListener,
        sessionProcessTerminatedListener,
        sessionProcessLifetime,
        aspireHostProjectPath
    )
}