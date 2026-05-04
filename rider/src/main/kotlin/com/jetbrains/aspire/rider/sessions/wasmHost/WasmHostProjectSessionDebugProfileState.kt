package com.jetbrains.aspire.rider.sessions.wasmHost

import com.intellij.execution.CantRunException
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.ui.ConsoleView
import com.intellij.ide.browsers.StartBrowserSettings
import com.intellij.openapi.diagnostic.logger
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.aspire.AspireService
import com.jetbrains.aspire.rider.sessions.projectLaunchers.DotNetSessionDebugProfileState
import com.jetbrains.rider.debugger.DebuggerHelperHost
import com.jetbrains.rider.debugger.DebuggerWorkerProcessHandler
import com.jetbrains.rider.debugger.editAndContinue.web.BrowserRefreshAgentHost
import com.jetbrains.rider.debugger.editAndContinue.web.BrowserRefreshAgentManager
import com.jetbrains.rider.debugger.wasm.BrowserHub
import com.jetbrains.rider.debugger.wasm.BrowserHubManager
import com.jetbrains.rider.run.configurations.HotReloadEnvironmentBuilder
import com.jetbrains.rider.runtime.DotNetExecutable
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntime
import java.nio.file.Path
import kotlin.io.path.absolutePathString

internal class WasmHostProjectSessionDebugProfileState(
    sessionId: String,
    private val projectPath: Path,
    dotnetExecutable: DotNetExecutable,
    dotnetRuntime: DotNetCoreRuntime,
    environment: ExecutionEnvironment,
    private val browserSettings: StartBrowserSettings?,
    sessionProcessEventListener: ProcessListener,
    private val sessionProcessLifetime: Lifetime
) : DotNetSessionDebugProfileState(
    sessionId,
    dotnetExecutable,
    dotnetRuntime,
    environment,
    sessionProcessEventListener,
    sessionProcessLifetime
) {
    companion object {
        private val LOG = logger<WasmHostProjectSessionDebugProfileState>()
    }

    lateinit var browserRefreshHost: BrowserRefreshAgentHost
    lateinit var browserHub: BrowserHub
    val browserHubLifetimeDef = AspireService.getInstance(project).lifetime.createNested()

    override suspend fun beforeWorkerStart(lifetime: Lifetime, environment: ExecutionEnvironment) {
        browserRefreshHost =
            BrowserRefreshAgentManager
                .getInstance(environment.project)
                .startHost(
                    dotNetExecutable.projectTfm,
                    dotNetExecutable.environmentVariables,
                    lifetime
                )

        browserHub =
            BrowserHubManager
                .getInstance(environment.project)
                .start(dotNetExecutable.environmentVariables, browserHubLifetimeDef)
    }

    override suspend fun createWorkerRunInfo(
        lifetime: Lifetime,
        helper: DebuggerHelperHost,
        port: Int
    ) = super.createWorkerRunInfo(lifetime, helper, port).apply {
        val hotReloadEnvs = HotReloadEnvironmentBuilder()
            .addDeltaApplier()
            .addBlazorRefreshClient()
            .setBlazorRefreshServerUrls(browserRefreshHost.wsUrls, browserRefreshHost.serverKey)
            .build()
        commandLine.environment.putAll(hotReloadEnvs)
    }

    override suspend fun execute(
        workerConsole: ConsoleView,
        workerProcessHandler: DebuggerWorkerProcessHandler,
        lifetime: Lifetime
    ): ExecutionResult {
        val connectedBrowser = startBrowserAndAttach(
            browserHub,
            browserHubLifetimeDef,
            browserSettings,
            sessionProcessLifetime,
            project
        )
        if (connectedBrowser == null) {
            LOG.warn("Unable to obtain connected browser")
            throw CantRunException("Unable to obtain connected browser")
        }

        val hostExecutionResult = super.execute(workerConsole, workerProcessHandler, lifetime)

        executeClient(
            projectPath.absolutePathString(),
            browserHub,
            browserRefreshHost,
            connectedBrowser,
            browserSettings,
            sessionProcessLifetime,
            project
        )

        return hostExecutionResult
    }
}