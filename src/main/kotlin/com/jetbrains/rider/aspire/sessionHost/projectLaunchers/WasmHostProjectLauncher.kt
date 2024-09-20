@file:Suppress("DuplicatedCode", "LoggingSimilarMessage")

package com.jetbrains.rider.aspire.sessionHost.projectLaunchers

import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.aspire.generated.SessionModel
import com.jetbrains.rider.aspire.sessionHost.SessionEvent
import com.jetbrains.rider.aspire.sessionHost.SessionManager
import com.jetbrains.rider.aspire.sessionHost.hotReload.WasmHostHotReloadConfigurationExtension
import com.jetbrains.rider.nuget.PackageVersionResolution
import com.jetbrains.rider.nuget.RiderNuGetInstalledPackageCheckerHost
import com.jetbrains.rider.run.TerminalProcessHandler
import com.jetbrains.rider.run.createRunCommandLine
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.io.path.Path

class WasmHostProjectLauncher : AspireProjectBaseLauncher() {
    companion object {
        private val LOG = logger<WasmHostProjectLauncher>()

        private const val DEV_SERVER_NUGET = "Microsoft.AspNetCore.Components.WebAssembly.DevServer"
        private const val SERVER_NUGET = "Microsoft.AspNetCore.Components.WebAssembly.Server"
    }

    override val priority = 1

    override val hotReloadExtension = WasmHostHotReloadConfigurationExtension()

    override suspend fun isApplicable(projectPath: String, project: Project): Boolean {
        val nugetChecker = RiderNuGetInstalledPackageCheckerHost.getInstance(project)
        return nugetChecker.isPackageInstalled(PackageVersionResolution.EXACT, projectPath, DEV_SERVER_NUGET) ||
                nugetChecker.isPackageInstalled(PackageVersionResolution.EXACT, projectPath, SERVER_NUGET)
    }

    override suspend fun launchRunSession(
        sessionId: String,
        sessionModel: SessionModel,
        sessionLifetime: Lifetime,
        sessionEvents: MutableSharedFlow<SessionEvent>,
        project: Project
    ) {
        val executable = getExecutable(sessionModel, project) ?: return
        val runtime = getRuntime(executable, project) ?: return

        LOG.trace { "Starting run session for project ${sessionModel.projectPath}" }

        val sessionProjectPath = Path(sessionModel.projectPath)
        val (executableToRun, hotReloadProcessListener) = enableHotReload(
            executable,
            sessionProjectPath,
            sessionModel.launchProfile,
            sessionLifetime,
            project
        )

        val commandLine = executableToRun.createRunCommandLine(runtime)
        val handler = TerminalProcessHandler(project, commandLine, commandLine.commandLineString)

        handler.addProcessListener(object : ProcessAdapter() {
            override fun processTerminated(event: ProcessEvent) {
                SessionManager.getInstance(project).sessionProcessWasTerminated(sessionId, event.exitCode, event.text)
            }
        })

        sessionLifetime.onTermination {
            if (!handler.isProcessTerminating && !handler.isProcessTerminated) {
                LOG.trace("Killing session process handler (id: $sessionId)")
                handler.killProcess()
            }
        }

        hotReloadProcessListener?.let { handler.addProcessListener(it) }

        subscribeToSessionEvents(sessionId, handler, sessionEvents)

        handler.startNotify()
    }

    override suspend fun launchDebugSession(
        sessionId: String,
        sessionModel: SessionModel,
        sessionLifetime: Lifetime,
        sessionEvents: MutableSharedFlow<SessionEvent>,
        project: Project
    ) {

    }
}