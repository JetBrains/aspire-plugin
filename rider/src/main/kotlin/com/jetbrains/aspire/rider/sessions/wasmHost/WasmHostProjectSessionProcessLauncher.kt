package com.jetbrains.aspire.rider.sessions.wasmHost

import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.runners.ProgramRunner
import com.intellij.ide.browsers.StartBrowserSettings
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.aspire.run.host.AspireHostConfiguration
import com.jetbrains.aspire.rider.sessions.DotNetProjectSessionExecutableFactory
import com.jetbrains.aspire.sessions.findRunnableProjectByPath
import com.jetbrains.aspire.rider.sessions.projectLaunchers.DotNetSessionWithHotReloadProcessLauncher
import com.jetbrains.aspire.sessions.DotNetSessionLaunchConfiguration
import com.jetbrains.rider.nuget.PackageVersionResolution
import com.jetbrains.rider.nuget.RiderNuGetInstalledPackageCheckerHost
import com.jetbrains.rider.runtime.DotNetExecutable
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntime
import java.nio.file.Path
import kotlin.io.path.Path

/**
 * Launches a Blazor WASM host project from an Aspire session request.
 */
internal class WasmHostProjectSessionProcessLauncher : DotNetSessionWithHotReloadProcessLauncher() {
    companion object {
        private const val DEV_SERVER_NUGET = "Microsoft.AspNetCore.Components.WebAssembly.DevServer"
        private const val SERVER_NUGET = "Microsoft.AspNetCore.Components.WebAssembly.Server"

        private val LOG = logger<WasmHostProjectSessionProcessLauncher>()
    }

    override val priority = 3

    override val hotReloadExtension = WasmHostHotReloadConfigurationExtension()

    override suspend fun isApplicable(projectPath: String, project: Project): Boolean {
        val runnableProject = findRunnableProjectByPath(Path(projectPath), project)
        if (runnableProject == null) {
            LOG.trace { "Can't find runnable project with path: $projectPath. Skip launcher" }
            return false
        }

        val nugetChecker = RiderNuGetInstalledPackageCheckerHost.getInstance(project)
        return nugetChecker.isPackageInstalled(PackageVersionResolution.EXACT, projectPath, DEV_SERVER_NUGET) ||
                nugetChecker.isPackageInstalled(PackageVersionResolution.EXACT, projectPath, SERVER_NUGET)
    }

    override fun getRunProfile(
        sessionId: String,
        projectPath: Path,
        dotnetExecutable: DotNetExecutable,
        dotnetRuntime: DotNetCoreRuntime,
        sessionProcessEventListener: ProcessListener,
        sessionProcessLifetime: Lifetime,
        aspireHostProjectPath: Path?
    ) = WasmHostProjectSessionRunProfile(
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
    ) = WasmHostProjectSessionDebugProfile(
        sessionId,
        projectPath,
        dotnetExecutable,
        dotnetRuntime,
        browserSettings,
        sessionProcessEventListener,
        sessionProcessLifetime,
        aspireHostProjectPath
    )

    override suspend fun getDotNetExecutable(
        launchConfiguration: DotNetSessionLaunchConfiguration,
        isDebugSession: Boolean,
        hostRunConfiguration: AspireHostConfiguration?,
        project: Project
    ): Pair<DotNetExecutable, StartBrowserSettings?>? {
        val factory = DotNetProjectSessionExecutableFactory.getInstance(project)
        val addBrowserAction = !isDebugSession
        val executable = factory.createExecutable(launchConfiguration, hostRunConfiguration, addBrowserAction)
        if (executable == null) {
            LOG.warn("Unable to create executable for project: ${launchConfiguration.projectPath}")
        }

        return executable
    }

    override fun ExecutionEnvironmentBuilder.modifyExecutionEnvironmentForDebug(): ExecutionEnvironmentBuilder {
        val defaultRunner = ProgramRunner.findRunnerById(WasmHostProjectSessionDebugProgramRunner.ID)
        return if (defaultRunner != null) {
            this.runner(defaultRunner)
        } else {
            this
        }
    }
}