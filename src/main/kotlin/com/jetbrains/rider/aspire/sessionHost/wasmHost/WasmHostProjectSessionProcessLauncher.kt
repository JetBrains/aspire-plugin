package com.jetbrains.rider.aspire.sessionHost.wasmHost

import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionResult
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.ide.browsers.StartBrowserSettings
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.util.Url
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.aspire.AspireService
import com.jetbrains.rider.aspire.generated.SessionModel
import com.jetbrains.rider.aspire.run.AspireHostConfiguration
import com.jetbrains.rider.aspire.sessionHost.projectLaunchers.BaseProjectSessionProcessLauncher
import com.jetbrains.rider.debugger.editAndContinue.web.BrowserRefreshAgentManager
import com.jetbrains.rider.debugger.wasm.BrowserHubManager
import com.jetbrains.rider.nuget.PackageVersionResolution
import com.jetbrains.rider.nuget.RiderNuGetInstalledPackageCheckerHost
import com.jetbrains.rider.run.ConsoleKind
import com.jetbrains.rider.run.IDebuggerOutputListener
import com.jetbrains.rider.run.configurations.HotReloadEnvironmentBuilder
import com.jetbrains.rider.run.createConsole
import com.jetbrains.rider.runtime.DotNetExecutable
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntime
import icons.RiderIcons
import kotlinx.coroutines.Dispatchers
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
        val nugetChecker = RiderNuGetInstalledPackageCheckerHost.Companion.getInstance(project)
        return nugetChecker.isPackageInstalled(PackageVersionResolution.EXACT, projectPath, DEV_SERVER_NUGET) ||
                nugetChecker.isPackageInstalled(PackageVersionResolution.EXACT, projectPath, SERVER_NUGET)
    }

    override suspend fun launchRunProcess(
        sessionId: String,
        sessionModel: SessionModel,
        sessionProcessEventListener: ProcessListener,
        sessionProcessTerminatedListener: ProcessListener,
        sessionProcessLifetime: Lifetime,
        hostRunConfiguration: AspireHostConfiguration?,
        project: Project
    ) {
        LOG.trace { "Starting wasm run session for project ${sessionModel.projectPath}" }

        val (executable, browserSettings) = getDotNetExecutable(sessionModel, hostRunConfiguration, project) ?: return
        val (executableWithHotReload, hotReloadProcessListener) = enableHotReload(
            executable,
            Path(sessionModel.projectPath),
            sessionModel.launchProfile,
            sessionProcessLifetime,
            project
        )
        val runtime = getDotNetRuntime(executable, project) ?: return

        val handler = createRunProcessHandler(
            sessionId,
            executableWithHotReload,
            runtime,
            sessionProcessEventListener,
            sessionProcessTerminatedListener,
            hotReloadProcessListener,
            sessionProcessLifetime,
            project,
        )

        startBrowser(hostRunConfiguration, browserSettings, handler)

        handler.startNotify()
    }

    override suspend fun launchDebugProcess(
        sessionId: String,
        sessionModel: SessionModel,
        sessionProcessEventListener: ProcessListener,
        sessionProcessTerminatedListener: ProcessListener,
        sessionProcessLifetime: Lifetime,
        hostRunConfiguration: AspireHostConfiguration?,
        project: Project
    ) {
        LOG.trace { "Starting wasm debug session for project ${sessionModel.projectPath}" }

        val (executable, browserSettings) = getDotNetExecutable(sessionModel, hostRunConfiguration, project) ?: return
        val runtime = getDotNetRuntime(executable, project) ?: return

        withContext(Dispatchers.EDT) {
            createAndStartDebugSession(
                sessionId,
                Path(sessionModel.projectPath),
                executable,
                runtime,
                browserSettings,
                sessionProcessEventListener,
                sessionProcessTerminatedListener,
                sessionProcessLifetime,
                project
            )
        }
    }

    private suspend fun createAndStartDebugSession(
        sessionId: String,
        sessionProjectPath: Path,
        executable: DotNetExecutable,
        runtime: DotNetCoreRuntime,
        browserSettings: StartBrowserSettings?,
        sessionProcessEventListener: ProcessListener,
        sessionProcessTerminatedListener: ProcessListener,
        sessionProcessLifetime: Lifetime,
        project: Project
    ) {
        val debuggerSessionId = ExecutionEnvironment.getNextUnusedExecutionId()

        val browserRefreshHost = BrowserRefreshAgentManager.Companion
            .getInstance(project)
            .startHost(executable.projectTfm, sessionProcessLifetime)
        val browserHubLifetimeDef = AspireService.Companion.getInstance(project).lifetime.createNested()
        val browserHub = BrowserHubManager.Companion
            .getInstance(project)
            .start(browserHubLifetimeDef.lifetime)

        val debuggerWorkerSession = initDebuggerSession(
            sessionId,
            debuggerSessionId,
            executable,
            runtime,
            sessionProcessEventListener,
            sessionProcessTerminatedListener,
            sessionProcessLifetime,
            project,
            { modifyDebuggerWorkerCmd(browserRefreshHost.wsUrls, browserRefreshHost.serverKey, it) }
        )

        val connectedBrowser = startBrowserAndAttach(
            browserHub,
            browserHubLifetimeDef,
            browserSettings,
            sessionProcessLifetime,
            project
        )
        if (connectedBrowser == null) {
            LOG.warn("Unable to obtain connected browser")
            return
        }

        val executionResult = executeDebuggerSession(debuggerWorkerSession, project)

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
            executionResult.executionConsole,
            null,
            project,
            sessionProcessLifetime,
            executionResult.processHandler,
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

    private fun executeDebuggerSession(
        session: DebuggerWorkerSession,
        project: Project
    ): ExecutionResult {
        val console = createConsole(
            ConsoleKind.Normal,
            session.debuggerWorkerProcessHandler.debuggerWorkerRealHandler,
            project
        )

        return DefaultExecutionResult(console, session.debuggerWorkerProcessHandler)
    }
}