package com.jetbrains.rider.aspire.sessionHost.wasmHost

import com.intellij.execution.process.ProcessListener
import com.intellij.ide.browsers.StartBrowserSettings
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.aspire.sessionHost.projectLaunchers.BaseProjectSessionProcessLauncher
import com.jetbrains.rider.nuget.PackageVersionResolution
import com.jetbrains.rider.nuget.RiderNuGetInstalledPackageCheckerHost
import com.jetbrains.rider.runtime.DotNetExecutable
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntime
import java.nio.file.Path
import kotlin.io.path.nameWithoutExtension

class WasmHostProjectSessionProcessLauncher : BaseProjectSessionProcessLauncher() {
    companion object {
        private const val DEV_SERVER_NUGET = "Microsoft.AspNetCore.Components.WebAssembly.DevServer"
        private const val SERVER_NUGET = "Microsoft.AspNetCore.Components.WebAssembly.Server"
    }

    override val priority = 1

    override val hotReloadExtension = WasmHostHotReloadConfigurationExtension()

    override suspend fun isApplicable(projectPath: String, project: Project): Boolean {
        val nugetChecker = RiderNuGetInstalledPackageCheckerHost.Companion.getInstance(project)
        return nugetChecker.isPackageInstalled(PackageVersionResolution.EXACT, projectPath, DEV_SERVER_NUGET) ||
                nugetChecker.isPackageInstalled(PackageVersionResolution.EXACT, projectPath, SERVER_NUGET)
    }

    override fun getRunProfile(
        sessionId: String,
        projectName: String,
        dotnetExecutable: DotNetExecutable,
        dotnetRuntime: DotNetCoreRuntime,
        sessionProcessEventListener: ProcessListener,
        sessionProcessTerminatedListener: ProcessListener,
        sessionProcessLifetime: Lifetime
    ) = WasmHostSessionRunProfile(
        sessionId,
        projectName,
        dotnetExecutable,
        dotnetRuntime,
        sessionProcessEventListener,
        sessionProcessTerminatedListener,
        sessionProcessLifetime
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
        sessionProcessLifetime: Lifetime
    ) = WasmHostProjectSessionDebugProfile(
        sessionId,
        projectPath.nameWithoutExtension,
        projectPath,
        dotnetExecutable,
        dotnetRuntime,
        browserSettings,
        sessionProcessEventListener,
        sessionProcessTerminatedListener,
        sessionProcessLifetime
    )
}