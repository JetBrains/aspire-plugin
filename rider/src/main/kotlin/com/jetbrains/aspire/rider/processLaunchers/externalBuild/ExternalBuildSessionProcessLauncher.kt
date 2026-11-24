package com.jetbrains.aspire.rider.processLaunchers.externalBuild

import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ProgramRunner
import com.intellij.ide.browsers.StartBrowserSettings
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.rider.processLaunchers.DotNetSessionWithHotReloadProcessLauncher
import com.jetbrains.aspire.rider.processLaunchers.SessionExecutable
import com.jetbrains.aspire.rider.processLaunchers.SessionProcessLaunchConfiguration
import com.jetbrains.aspire.rider.processLaunchers.dotnetProject.DotNetProjectSessionDebugProfile
import com.jetbrains.aspire.rider.processLaunchers.dotnetProject.DotNetProjectSessionRunProfile
import com.jetbrains.aspire.rider.run.AspireRiderRunConfiguration
import com.jetbrains.aspire.rider.util.pathWithoutExeOrDllExtension
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.model.RunnableProjectKind
import com.jetbrains.rider.run.aspire.DotNetProjectHotReloadConfigurationExtension
import com.jetbrains.rider.run.configurations.RunnableProjectKinds
import com.jetbrains.rider.runtime.DotNetExecutable
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntime
import java.nio.file.Path
import kotlin.io.path.Path

/**
 * Launches a .NET project that was built externally by the AppHost (`project-with-external-build.v1`).
 */
internal class ExternalBuildSessionProcessLauncher : DotNetSessionWithHotReloadProcessLauncher() {
    companion object {
        private val LOG = logger<ExternalBuildSessionProcessLauncher>()
    }

    override val priority = 12

    @Suppress("JetBrainsInternalApiUsage")
    override val hotReloadExtension = DotNetProjectHotReloadConfigurationExtension()
    override val supportedRunnableProjectKinds: List<RunnableProjectKind> =
        listOf(RunnableProjectKinds.DotNetCore, RunnableProjectKinds.Wpf)

    // The launcher is selected explicitly for `project-with-external-build.v1` sessions
    override suspend fun isApplicable(projectPath: Path, project: Project) = false

    override suspend fun getDotNetExecutable(
        launchConfiguration: SessionProcessLaunchConfiguration,
        isDebugSession: Boolean,
        aspireRunConfiguration: AspireRiderRunConfiguration?,
        project: Project,
        sessionProcessLifetime: Lifetime
    ): SessionExecutable? {
        return ExternalBuildSessionExecutableFactory.getInstance(project)
            .createExecutable(launchConfiguration, aspireRunConfiguration)
            .getOrNull()
    }

    // Hot Reload applies changes compiled from the solution project.
    // It is only safe if the process runs the same output that Rider builds for this project.
    override suspend fun modifyDotNetExecutable(
        executable: DotNetExecutable,
        sessionProjectPath: Path,
        launchProfile: String?,
        lifetime: Lifetime,
        project: Project
    ): Pair<DotNetExecutable, ProgramRunner.Callback?> {
        // An output is either the managed assembly or its apphost next to it
        val runnableProject = findSupportedRunnableProjectByPath(sessionProjectPath, project)
        val executableOutput = Path(executable.exePath).normalize().pathWithoutExeOrDllExtension
        val runsSolutionProjectOutput = runnableProject?.projectOutputs?.any {
            Path(it.exePath).normalize().pathWithoutExeOrDllExtension == executableOutput
        } == true
        if (!runsSolutionProjectOutput) {
            LOG.info("Hot Reload is disabled for $sessionProjectPath because the process doesn't run the solution project output: ${executable.exePath}")
            return executable to null
        }

        return super.modifyDotNetExecutable(executable, sessionProjectPath, launchProfile, lifetime, project)
    }

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
}
