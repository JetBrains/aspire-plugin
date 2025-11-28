package com.jetbrains.aspire.sessions.dotnetProject

import com.intellij.execution.process.ProcessListener
import com.intellij.ide.browsers.StartBrowserSettings
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.aspire.generated.CreateSessionRequest
import com.jetbrains.aspire.run.host.AspireHostConfiguration
import com.jetbrains.aspire.sessions.DotNetProjectSessionExecutableFactory
import com.jetbrains.aspire.sessions.projectLaunchers.DotNetExecutableWithHotReloadSessionProcessLauncher
import com.jetbrains.rider.runtime.DotNetExecutable
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntime
import java.nio.file.Path

/**
 * Launches a regular .NET project from an Aspire session request.
 */
internal class DotNetProjectSessionProcessLauncher : DotNetExecutableWithHotReloadSessionProcessLauncher() {
    companion object {
        private val LOG = logger<DotNetProjectSessionProcessLauncher>()
    }

    override val priority = 10

    override val hotReloadExtension = DotNetProjectHotReloadConfigurationExtension()

    override suspend fun isApplicable(projectPath: String, project: Project) = true

    override fun getRunProfile(
        sessionId: String,
        projectPath: Path,
        dotnetExecutable: DotNetExecutable,
        dotnetRuntime: DotNetCoreRuntime,
        sessionProcessEventListener: ProcessListener,
        sessionProcessLifetime: Lifetime,
        aspireHostProjectPath: Path?
    ) = DotNetProjectSessionRunProfile(
        sessionId,
        projectPath,
        dotnetExecutable,
        dotnetRuntime,
        sessionProcessEventListener,
        sessionProcessLifetime,
        aspireHostProjectPath
    )

    override fun getDebugProfile(
        sessionId: String,
        projectPath: Path,
        dotnetExecutable: DotNetExecutable,
        dotnetRuntime: DotNetCoreRuntime,
        browserSettings: StartBrowserSettings?,
        sessionProcessEventListener: ProcessListener,
        sessionProcessLifetime: Lifetime,
        aspireHostProjectPath: Path?
    ) = DotNetProjectSessionDebugProfile(
        sessionId,
        projectPath,
        dotnetExecutable,
        dotnetRuntime,
        sessionProcessEventListener,
        sessionProcessLifetime,
        aspireHostProjectPath
    )

    override suspend fun getDotNetExecutable(
        sessionModel: CreateSessionRequest,
        isDebugSession: Boolean,
        hostRunConfiguration: AspireHostConfiguration?,
        project: Project
    ): Pair<DotNetExecutable, StartBrowserSettings?>? {
        val factory = DotNetProjectSessionExecutableFactory.getInstance(project)
        val executable = factory.createExecutable(sessionModel, hostRunConfiguration, true)
        if (executable == null) {
            LOG.warn("Unable to create executable for project: ${sessionModel.projectPath}")
        }

        return executable
    }
}