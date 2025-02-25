package com.jetbrains.rider.aspire.sessionHost.wasmHost

import com.intellij.execution.process.ProcessListener
import com.intellij.ide.browsers.StartBrowserSettings
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.aspire.generated.CreateSessionRequest
import com.jetbrains.rider.aspire.run.AspireHostConfiguration
import com.jetbrains.rider.aspire.sessionHost.SessionExecutableFactory
import com.jetbrains.rider.aspire.sessionHost.projectLaunchers.DotNetExecutableWithHotReloadSessionProcessLauncher
import com.jetbrains.rider.nuget.PackageVersionResolution
import com.jetbrains.rider.nuget.RiderNuGetInstalledPackageCheckerHost
import com.jetbrains.rider.runtime.DotNetExecutable
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntime
import java.nio.file.Path

/**
 * Provides support for the Blazor WASM projects.
 */
class WasmHostProjectSessionProcessLauncher : DotNetExecutableWithHotReloadSessionProcessLauncher() {
    companion object {
        private const val DEV_SERVER_NUGET = "Microsoft.AspNetCore.Components.WebAssembly.DevServer"
        private const val SERVER_NUGET = "Microsoft.AspNetCore.Components.WebAssembly.Server"

        private val LOG = logger<WasmHostProjectSessionProcessLauncher>()
    }

    override val priority = 3

    override val hotReloadExtension = WasmHostHotReloadConfigurationExtension()

    override suspend fun isApplicable(projectPath: String, project: Project): Boolean {
        val nugetChecker = RiderNuGetInstalledPackageCheckerHost.Companion.getInstance(project)
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
        sessionModel: CreateSessionRequest,
        isDebugSession: Boolean,
        hostRunConfiguration: AspireHostConfiguration?,
        project: Project
    ): Pair<DotNetExecutable, StartBrowserSettings?>? {
        val factory = SessionExecutableFactory.getInstance(project)
        val addBrowserAction = !isDebugSession
        val executable = factory.createExecutable(sessionModel, hostRunConfiguration, addBrowserAction)
        if (executable == null) {
            LOG.warn("Unable to create executable for project: ${sessionModel.projectPath}")
        }

        return executable
    }
}