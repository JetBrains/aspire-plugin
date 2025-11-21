package com.jetbrains.rider.aspire.rider.sessions.wasmHost

import com.intellij.execution.CantRunException
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.ide.browsers.StartBrowserSettings
import com.intellij.openapi.diagnostic.logger
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.aspire.AspireService
import com.jetbrains.rider.aspire.sessions.projectLaunchers.DotNetExecutableSessionDebugProfileState
import com.jetbrains.rider.debugger.DebuggerHelperHost
import com.jetbrains.rider.debugger.DebuggerWorkerProcessHandler
import com.jetbrains.rider.debugger.editAndContinue.web.BrowserRefreshAgentHost
import com.jetbrains.rider.debugger.wasm.BrowserHub
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
) : DotNetExecutableSessionDebugProfileState(
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

    var browserRefreshHost: BrowserRefreshAgentHost? = null
    var browserHub: BrowserHub? = null
    val browserHubLifetimeDef = AspireService.getInstance(project).lifetime.createNested()

    override suspend fun createWorkerRunInfo(
        lifetime: Lifetime,
        helper: DebuggerHelperHost,
        port: Int
    ) = super.createWorkerRunInfo(lifetime, helper, port).apply {
        browserRefreshHost?.let { host ->
            val hotReloadEnvs = HotReloadEnvironmentBuilder()
                .addDeltaApplier()
                .addBlazorRefreshClient()
                .setBlazorRefreshServerUrls(host.wsUrls, host.serverKey)
                .build()
            commandLine.environment.putAll(hotReloadEnvs)
        }
    }

    override suspend fun execute(
        executor: Executor,
        runner: ProgramRunner<*>,
        workerProcessHandler: DebuggerWorkerProcessHandler,
        lifetime: Lifetime
    ): ExecutionResult {
        val refresherHost = browserRefreshHost
        val hub = browserHub
        if (refresherHost == null || hub == null) {
            LOG.warn("Browser refresh host or browser host are not initialized yet")
            throw CantRunException("Browser refresh host or browser host are not initialized yet")
        }

        val connectedBrowser = startBrowserAndAttach(
            hub,
            browserHubLifetimeDef,
            browserSettings,
            sessionProcessLifetime,
            project
        )
        if (connectedBrowser == null) {
            LOG.warn("Unable to obtain connected browser")
            throw CantRunException("Unable to obtain connected browser")
        }

        val hostExecutionResult = super.execute(executor, runner, workerProcessHandler, lifetime)

        executeClient(
            projectPath.absolutePathString(),
            hub,
            refresherHost,
            connectedBrowser,
            browserSettings,
            sessionProcessLifetime,
            project
        )

        return hostExecutionResult
    }
}