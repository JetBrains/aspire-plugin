@file:Suppress("DuplicatedCode", "UnstableApiUsage")

package com.jetbrains.rider.aspire.sessionHost.projectLaunchers

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.ide.browsers.StartBrowserSettings
import com.intellij.ide.browsers.WebBrowser
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.util.Url
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.aspire.AspireService
import com.jetbrains.rider.aspire.generated.SessionModel
import com.jetbrains.rider.aspire.run.AspireHostConfiguration
import com.jetbrains.rider.aspire.sessionHost.SessionEvent
import com.jetbrains.rider.aspire.sessionHost.hotReload.WasmHostHotReloadConfigurationExtension
import com.jetbrains.rider.debugger.editAndContinue.web.BrowserRefreshAgentManager
import com.jetbrains.rider.debugger.wasm.BrowserHubManager
import com.jetbrains.rider.nuget.PackageVersionResolution
import com.jetbrains.rider.nuget.RiderNuGetInstalledPackageCheckerHost
import com.jetbrains.rider.run.IDebuggerOutputListener
import com.jetbrains.rider.run.configurations.HotReloadEnvironmentBuilder
import com.jetbrains.rider.runtime.DotNetExecutable
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntime
import icons.RiderIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.nameWithoutExtension

class WasmHostProjectSessionProcessLauncher : BaseProjectSessionProcessLauncher() {
    companion object {
        private val LOG = logger<WasmHostProjectSessionProcessLauncher>()

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

    override suspend fun launchRunProcess(
        sessionId: String,
        sessionModel: SessionModel,
        sessionProcessLifetime: Lifetime,
        sessionEvents: MutableSharedFlow<SessionEvent>,
        hostRunConfiguration: AspireHostConfiguration?,
        project: Project,
        sessionProcessHandlerTerminated: (Int, String?) -> Unit
    ) {
        val (executable, browserSettings) = getDotNetExecutable(sessionModel, hostRunConfiguration, project) ?: return
        val runtime = getDotNetRuntime(executable, project) ?: return

        LOG.trace { "Starting wasm run session for project ${sessionModel.projectPath}" }

        val sessionProjectPath = Path(sessionModel.projectPath)
        val (executableWithHotReload, hotReloadProcessListener) = enableHotReload(
            executable,
            sessionProjectPath,
            sessionModel.launchProfile,
            sessionProcessLifetime,
            project
        )

        val handler = createRunProcessHandler(
            sessionId,
            executableWithHotReload,
            runtime,
            hotReloadProcessListener,
            sessionProcessLifetime,
            sessionEvents,
            project,
            sessionProcessHandlerTerminated
        )

        startBrowser(hostRunConfiguration, browserSettings, handler)

        handler.startNotify()
    }

    override suspend fun launchDebugProcess(
        sessionId: String,
        sessionModel: SessionModel,
        sessionProcessLifetime: Lifetime,
        sessionEvents: MutableSharedFlow<SessionEvent>,
        hostRunConfiguration: AspireHostConfiguration?,
        project: Project,
        sessionProcessHandlerTerminated: (Int, String?) -> Unit
    ) {
        val (executable, browserSettings) = getDotNetExecutable(sessionModel, hostRunConfiguration, project) ?: return
        val runtime = getDotNetRuntime(executable, project) ?: return

        LOG.trace { "Starting wasm debug session for project ${sessionModel.projectPath}" }

        val sessionProjectPath = Path(sessionModel.projectPath)
        withContext(Dispatchers.EDT) {
            createAndStartDebugSession(
                sessionId,
                sessionProjectPath,
                executable,
                runtime,
                sessionEvents,
                sessionProcessLifetime,
                project,
                browserSettings,
                sessionProcessHandlerTerminated
            )
        }
    }

    private suspend fun createAndStartDebugSession(
        sessionId: String,
        sessionProjectPath: Path,
        executable: DotNetExecutable,
        runtime: DotNetCoreRuntime,
        sessionEvents: MutableSharedFlow<SessionEvent>,
        sessionProcessLifetime: Lifetime,
        project: Project,
        browserSettings: StartBrowserSettings?,
        sessionProcessHandlerTerminated: (Int, String?) -> Unit
    ) {
        val browserRefreshHost = BrowserRefreshAgentManager
            .getInstance(project)
            .startHost(executable.projectTfm, sessionProcessLifetime)
        val browserHubLifetimeDef = AspireService.getInstance(project).lifetime.createNested()
        val browserHub = BrowserHubManager
            .getInstance(project)
            .start(browserHubLifetimeDef.lifetime)

        val debuggerSessionId = ExecutionEnvironment.getNextUnusedExecutionId()
        val debuggerWorkerSession = prepareDebuggerWorkerSession(
            sessionId,
            debuggerSessionId,
            executable,
            runtime,
            sessionEvents,
            sessionProcessLifetime,
            project,
            { modifyDebuggerWorkerCmd(browserRefreshHost.wsUrls, browserRefreshHost.serverKey, it) },
            sessionProcessHandlerTerminated
        )

        val connectedBrowser = startBrowserAndAttach(
            browserHub,
            browserHubLifetimeDef,
            browserSettings,
            sessionProcessLifetime,
            project
        ) ?: return

        executeClient(
            sessionProjectPath.absolutePathString(),
            browserHub,
            browserRefreshHost,
            connectedBrowser,
            browserSettings,
            sessionProcessLifetime,
            project
        )

        createAndStartSession(
            debuggerWorkerSession.console,
            null,
            project,
            sessionProcessLifetime,
            debuggerWorkerSession.debuggerWorkerProcessHandler,
            debuggerWorkerSession.protocol,
            debuggerWorkerSession.debugSessionModel,
            object : IDebuggerOutputListener {},
            debuggerSessionId,
            browserRefreshHost
        ) { xDebuggerManager, xDebugProcessStarter ->
            xDebuggerManager.startSessionAndShowTab(
                sessionProjectPath.nameWithoutExtension,
                RiderIcons.RunConfigurations.DotNetProject,
                null,
                false,
                xDebugProcessStarter
            )
        }
    }

    private fun modifyDebuggerWorkerCmd(
        wsUrls: List<Url>,
        serverKey: String,
        debuggerWorkerCmd: GeneralCommandLine
    ) {
        val hotReloadEnvs = HotReloadEnvironmentBuilder()
            .addDeltaApplier()
            .addBlazorRefreshClient()
            .setBlazorRefreshServerUrls(wsUrls, serverKey)
            .build()
        debuggerWorkerCmd.environment.putAll(hotReloadEnvs)
    }
}