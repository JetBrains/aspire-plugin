package com.jetbrains.rider.aspire.sessionHost.wasmHost

import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ExecutionConsole
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.jetbrains.rd.framework.IProtocol
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.debugger.DotNetDebugRunner
import com.jetbrains.rider.debugger.actions.utils.OptionsUtil
import com.jetbrains.rider.debugger.editAndContinue.web.BrowserRefreshAgentHost
import com.jetbrains.rider.debugger.wasm.host.WasmHostDebugProcess
import com.jetbrains.rider.model.debuggerWorker.DotNetDebuggerSessionModel
import com.jetbrains.rider.run.IDebuggerOutputListener

fun createAndStartSession(
    executionConsole: ExecutionConsole,
    env: ExecutionEnvironment?,
    project: Project,
    sessionLifetime: Lifetime,
    processHandler: ProcessHandler,
    protocol: IProtocol,
    sessionModel: DotNetDebuggerSessionModel,
    outputEventsListener: IDebuggerOutputListener,
    executionId: Long,
    browserRefreshHost: BrowserRefreshAgentHost,
    xDebugStarter: (XDebuggerManager, XDebugProcessStarter) -> XDebugSession
): XDebugSession {
    val debuggerManager = XDebuggerManager.getInstance(project)

    val fireInitializedManually = env?.getUserData(DotNetDebugRunner.FIRE_INITIALIZED_MANUALLY) ?: false

    val newSession = xDebugStarter(debuggerManager, object : XDebugProcessStarter() {
        override fun start(session: XDebugSession) = WasmHostDebugProcess(
            sessionLifetime,
            session,
            processHandler,
            executionConsole,
            protocol,
            sessionModel,
            fireInitializedManually,
            outputEventsListener,
            OptionsUtil.toDebugKind(sessionModel.sessionProperties.debugKind.valueOrNull),
            project,
            executionId,
            browserRefreshHost
        )
    })

    return newSession
}