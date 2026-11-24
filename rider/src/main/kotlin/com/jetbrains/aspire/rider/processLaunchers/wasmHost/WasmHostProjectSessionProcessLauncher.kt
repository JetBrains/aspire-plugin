package com.jetbrains.aspire.rider.processLaunchers.wasmHost

import com.intellij.execution.process.ProcessListener
import com.intellij.ide.browsers.StartBrowserSettings
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.rider.processLaunchers.DotNetProjectSessionExecutableFactory
import com.jetbrains.aspire.rider.processLaunchers.DotNetSessionWithHotReloadProcessLauncher
import com.jetbrains.aspire.rider.processLaunchers.SessionExecutable
import com.jetbrains.aspire.rider.processLaunchers.SessionProcessLaunchConfiguration
import com.jetbrains.aspire.rider.run.AspireRiderRunConfiguration
import com.jetbrains.aspire.rider.util.findRunnableProjectByPath
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.model.RunnableProjectKind
import com.jetbrains.rider.nuget.PackageVersionResolution
import com.jetbrains.rider.nuget.RiderNuGetInstalledPackageCheckerHost
import com.jetbrains.rider.run.aspire.WasmHostHotReloadConfigurationExtension
import com.jetbrains.rider.run.configurations.RunnableProjectKinds
import com.jetbrains.rider.runtime.DotNetExecutable
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntime
import java.nio.file.Path
import kotlin.io.path.absolutePathString

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

    @Suppress("JetBrainsInternalApiUsage")
    override val hotReloadExtension = WasmHostHotReloadConfigurationExtension()
    override val supportedRunnableProjectKinds: List<RunnableProjectKind> = listOf(RunnableProjectKinds.DotNetCore)

    override suspend fun isApplicable(projectPath: Path, project: Project): Boolean {
        val runnableProject = findRunnableProjectByPath(projectPath, project)
        if (runnableProject == null) {
            LOG.trace { "Can't find runnable project with path: $projectPath. Skip launcher" }
            return false
        }

        val projectPathString = projectPath.absolutePathString()
        val nugetChecker = RiderNuGetInstalledPackageCheckerHost.getInstance(project)
        return nugetChecker.isPackageInstalled(PackageVersionResolution.EXACT, projectPathString, DEV_SERVER_NUGET) ||
                nugetChecker.isPackageInstalled(PackageVersionResolution.EXACT, projectPathString, SERVER_NUGET)
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
        launchConfiguration: SessionProcessLaunchConfiguration,
        isDebugSession: Boolean,
        aspireRunConfiguration: AspireRiderRunConfiguration?,
        project: Project,
        sessionProcessLifetime: Lifetime
    ): SessionExecutable? {
        val factory = DotNetProjectSessionExecutableFactory.getInstance(project)
        val addBrowserAction = !isDebugSession
        val executable = factory.createExecutable(
            launchConfiguration,
            aspireRunConfiguration,
            addBrowserAction,
            supportedRunnableProjectKinds
        )
        if (executable == null) {
            LOG.warn("Unable to create executable for project: ${launchConfiguration.projectPath}")
        }

        return executable
    }
}