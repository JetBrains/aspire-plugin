@file:Suppress("DuplicatedCode")

package com.jetbrains.rider.aspire.sessionHost.projectLaunchers

import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.ide.browsers.WebBrowser
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.jetbrains.rd.framework.*
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.put
import com.jetbrains.rd.util.threading.coroutines.nextTrueValueAsync
import com.jetbrains.rdclient.protocol.RdDispatcher
import com.jetbrains.rider.aspire.generated.SessionModel
import com.jetbrains.rider.aspire.sessionHost.SessionEvent
import com.jetbrains.rider.aspire.sessionHost.hotReload.DotNetProjectHotReloadConfigurationExtension
import com.jetbrains.rider.debugger.DebuggerWorkerProcessHandler
import com.jetbrains.rider.debugger.RiderDebuggerWorkerModelManager
import com.jetbrains.rider.debugger.createAndStartSession
import com.jetbrains.rider.debugger.targets.DEBUGGER_WORKER_LAUNCHER
import com.jetbrains.rider.model.debuggerWorker.*
import com.jetbrains.rider.model.debuggerWorkerConnectionHelperModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.run.*
import com.jetbrains.rider.run.environment.ExecutableType
import com.jetbrains.rider.runtime.DotNetExecutable
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntime
import com.jetbrains.rider.util.NetUtils
import icons.RiderIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
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
        browser: WebBrowser?,
        project: Project,
        sessionProcessHandlerTerminated: (Int, String?) -> Unit
    ) {
        val (executable, browserSettings) = getDotNetExecutable(sessionModel, project) ?: return
        val runtime = getDotNetRuntime(executable, project) ?: return

        LOG.trace { "Starting run session for project ${sessionModel.projectPath}" }

        val sessionProjectPath = Path(sessionModel.projectPath)
        val (executableToRun, hotReloadProcessListener) = enableHotReload(
            executable,
            sessionProjectPath,
            sessionModel.launchProfile,
            sessionProcessLifetime,
            project
        )

        val commandLine = executableToRun.createRunCommandLine(runtime)
        val handler = TerminalProcessHandler(project, commandLine, commandLine.commandLineString)

        handler.addProcessListener(object : ProcessAdapter() {
            override fun processTerminated(event: ProcessEvent) {
                sessionProcessHandlerTerminated(event.exitCode, event.text)
            }
        })

        sessionProcessLifetime.onTermination {
            if (!handler.isProcessTerminating && !handler.isProcessTerminated) {
                LOG.trace("Killing run session process handler (id: $sessionId)")
                handler.killProcess()
            }
        }

        hotReloadProcessListener?.let { handler.addProcessListener(it) }

        subscribeToSessionEvents(sessionId, handler, sessionEvents)

        handler.startNotify()
    }

    override suspend fun launchDebugProcess(
        sessionId: String,
        sessionModel: SessionModel,
        sessionProcessLifetime: Lifetime,
        sessionEvents: MutableSharedFlow<SessionEvent>,
        browser: WebBrowser?,
        project: Project,
        sessionProcessHandlerTerminated: (Int, String?) -> Unit
    ) {
        val (executable, browserSettings) = getDotNetExecutable(sessionModel, project) ?: return
        val runtime = getDotNetRuntime(executable, project) ?: return

        LOG.trace { "Starting debug session for project ${sessionModel.projectPath}" }

        val startInfo = DotNetCoreExeStartInfo(
            DotNetCoreInfo(runtime.cliExePath),
            executable.projectTfm?.let { EncInfo(it) },
            executable.exePath,
            executable.workingDirectory,
            executable.programParameterString,
            executable.environmentVariables.toModelMap,
            executable.runtimeArguments,
            executable.executeAsIs,
            executable.useExternalConsole
        )
        val presentableCommandLine = createPresentableCommandLine(runtime, executable)

        val sessionProjectPath = Path(sessionModel.projectPath)
        withContext(Dispatchers.EDT) {
            createAndStartDebugSession(
                sessionId,
                sessionProjectPath.nameWithoutExtension,
                presentableCommandLine,
                startInfo,
                sessionEvents,
                sessionProcessLifetime,
                project,
                sessionProcessHandlerTerminated
            )
        }
    }

    private fun createPresentableCommandLine(runtime: DotNetCoreRuntime, executable: DotNetExecutable) = buildString {
        if (executable.executeAsIs) {
            append(executable.exePath)
        } else {
            append(runtime.cliExePath)
            append(" ")
            append(executable.exePath)
        }
        if (executable.programParameterString.isNotEmpty()) {
            append(" ")
            append(executable.programParameterString)
        }
    }

    private suspend fun createAndStartDebugSession(
        sessionId: String,
        @Nls sessionName: String,
        presentableCommandLine: String,
        startInfo: DebuggerStartInfoBase,
        sessionEvents: MutableSharedFlow<SessionEvent>,
        sessionProcessLifetime: Lifetime,
        project: Project,
        sessionProcessHandlerTerminated: (Int, String?) -> Unit
    ) {
        val debuggerSessionId = ExecutionEnvironment.getNextUnusedExecutionId()
        val frontendToDebuggerPort = NetUtils.findFreePort(57200)
        val backendToDebuggerPort = NetUtils.findFreePort(57300)

        val dispatcher = RdDispatcher(sessionProcessLifetime)
        val wire = SocketWire.Server(
            sessionProcessLifetime,
            dispatcher,
            port = frontendToDebuggerPort,
            optId = "FrontendToDebugWorker"
        )

        val sessionModel = DotNetDebuggerSessionModel(startInfo)
        sessionModel.sessionProperties.bindToSettings(sessionProcessLifetime, project).apply {
            debugKind.set(DebugKind.Live)
            remoteDebug.set(false)
            enableHeuristicPathResolve.set(false)
            editAndContinueEnabled.set(true)
        }

        val protocol = Protocol(
            "FrontendToDebuggerWorker",
            Serializers(),
            Identities(IdKind.Server),
            dispatcher,
            wire,
            sessionProcessLifetime
        )

        val workerModel = RiderDebuggerWorkerModelManager.createDebuggerModel(sessionProcessLifetime, protocol)
        workerModel.activeSession.set(sessionModel)

        val debuggerWorkerProcessHandler = createDebuggerWorkerProcessHandler(
            sessionId,
            presentableCommandLine,
            frontendToDebuggerPort,
            backendToDebuggerPort,
            workerModel,
            sessionProcessLifetime,
            sessionEvents,
            project
        )
        val console = createConsole(
            ConsoleKind.Normal,
            debuggerWorkerProcessHandler.debuggerWorkerRealHandler,
            project
        )

        wire.connected.nextTrueValueAsync(sessionProcessLifetime).await()
        project.solution.debuggerWorkerConnectionHelperModel.ports.put(
            sessionProcessLifetime,
            debuggerSessionId,
            backendToDebuggerPort
        )

        debuggerWorkerProcessHandler.addProcessListener(object : ProcessAdapter() {
            override fun processTerminated(event: ProcessEvent) {
                sessionProcessHandlerTerminated(event.exitCode, event.text)
            }
        })

        sessionProcessLifetime.onTermination {
            if (!debuggerWorkerProcessHandler.isProcessTerminating && !debuggerWorkerProcessHandler.isProcessTerminated) {
                LOG.trace("Killing session process handler (id: $sessionId)")
                debuggerWorkerProcessHandler.destroyProcess()
            }
        }

        createAndStartSession(
            console,
            null,
            project,
            sessionProcessLifetime,
            debuggerWorkerProcessHandler,
            protocol,
            sessionModel,
            object : IDebuggerOutputListener {},
            debuggerSessionId
        ) { xDebuggerManager, xDebugProcessStarter ->
            xDebuggerManager.startSessionAndShowTab(
                sessionName,
                RiderIcons.RunConfigurations.DotNetProject,
                null,
                false,
                xDebugProcessStarter
            )
        }
    }

    private fun createDebuggerWorkerProcessHandler(
        sessionId: String,
        presentableCommandLine: String,
        frontendToDebuggerPort: Int,
        backendToDebuggerPort: Int,
        workerModel: DebuggerWorkerModel,
        processLifetime: Lifetime,
        sessionEvents: MutableSharedFlow<SessionEvent>,
        project: Project
    ): DebuggerWorkerProcessHandler {
        val launcher = DEBUGGER_WORKER_LAUNCHER.getLauncher()
        val launcherCmd = DebugProfileStateBase.createWorkerCmdForLauncherInfo(
            ConsoleKind.Normal,
            frontendToDebuggerPort,
            launcher,
            ExecutableType.Console,
            true,
            false,
            true,
            "--backend-port=${backendToDebuggerPort}"
        )

        val handler = TerminalProcessHandler(project, launcherCmd, presentableCommandLine, false)

        val debuggerWorkerProcessHandler = DebuggerWorkerProcessHandler(
            handler,
            workerModel,
            false,
            "",
            processLifetime
        )

        subscribeToSessionEvents(
            sessionId,
            debuggerWorkerProcessHandler.debuggerWorkerRealHandler,
            sessionEvents
        )

        return debuggerWorkerProcessHandler
    }
}