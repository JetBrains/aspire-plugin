@file:Suppress("DuplicatedCode")

package com.jetbrains.rider.aspire.sessionHost.projectLaunchers

import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionResult
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.ide.browsers.StartBrowserSettings
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.aspire.generated.SessionModel
import com.jetbrains.rider.aspire.run.AspireHostConfiguration
import com.jetbrains.rider.aspire.sessionHost.SessionEvent
import com.jetbrains.rider.aspire.sessionHost.hotReload.DotNetProjectHotReloadConfigurationExtension
import com.jetbrains.rider.debugger.createAndStartSession
import com.jetbrains.rider.run.*
import com.jetbrains.rider.runtime.DotNetExecutable
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntime
import icons.RiderIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.nameWithoutExtension

class DotNetProjectSessionProcessLauncher : BaseProjectSessionProcessLauncher() {
    companion object {
        private val LOG = logger<DotNetProjectSessionProcessLauncher>()
    }

    override val priority = 10

    override val hotReloadExtension = DotNetProjectHotReloadConfigurationExtension()

    override suspend fun isApplicable(projectPath: String, project: Project) = true

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

        LOG.trace { "Starting run session for project ${sessionModel.projectPath}" }

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

        LOG.trace { "Starting debug session for project ${sessionModel.projectPath}" }

        withContext(Dispatchers.EDT) {
            createAndStartDebugSession(
                sessionId,
                Path(sessionModel.projectPath),
                executable,
                runtime,
                browserSettings,
                hostRunConfiguration,
                sessionEvents,
                sessionProcessLifetime,
                project,
                sessionProcessHandlerTerminated
            )
        }
    }

    private suspend fun createAndStartDebugSession(
        sessionId: String,
        sessionProjectPath: Path,
        dotnetExecutable: DotNetExecutable,
        dotnetRuntime: DotNetCoreRuntime,
        browserSettings: StartBrowserSettings?,
        hostRunConfiguration: AspireHostConfiguration?,
        sessionEvents: MutableSharedFlow<SessionEvent>,
        sessionProcessLifetime: Lifetime,
        project: Project,
        sessionProcessHandlerTerminated: (Int, String?) -> Unit
    ) {
        val debuggerSessionId = ExecutionEnvironment.getNextUnusedExecutionId()

        val debuggerWorkerSession = initDebuggerSession(
            sessionId,
            debuggerSessionId,
            dotnetExecutable,
            dotnetRuntime,
            sessionEvents,
            sessionProcessLifetime,
            project,
            { },
            sessionProcessHandlerTerminated
        )

        val executionResult =
            executeDebuggerSession(debuggerWorkerSession, browserSettings, hostRunConfiguration, project)

        createAndStartSession(
            executionResult.executionConsole,
            null,
            project,
            sessionProcessLifetime,
            executionResult.processHandler,
            debuggerWorkerSession.protocol,
            debuggerWorkerSession.debugSessionModel,
            object : IDebuggerOutputListener {},
            debuggerSessionId
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

    private fun executeDebuggerSession(
        session: DebuggerWorkerSession,
        browserSettings: StartBrowserSettings?,
        hostRunConfiguration: AspireHostConfiguration?,
        project: Project
    ): ExecutionResult {
        val console = createConsole(
            ConsoleKind.Normal,
            session.debuggerWorkerProcessHandler.debuggerWorkerRealHandler,
            project
        )

        startBrowser(hostRunConfiguration, browserSettings, session.debuggerWorkerProcessHandler)

        return DefaultExecutionResult(console, session.debuggerWorkerProcessHandler)
    }
}