@file:Suppress("UnstableApiUsage")

package com.jetbrains.rider.aspire.sessionHost.projectLaunchers

import com.intellij.BundleBase
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.KillableProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutputType
import com.intellij.execution.ui.ConsoleView
import com.intellij.ide.browsers.BrowserStarter
import com.intellij.ide.browsers.StartBrowserSettings
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.jetbrains.rd.framework.IdKind
import com.jetbrains.rd.framework.Identities
import com.jetbrains.rd.framework.Protocol
import com.jetbrains.rd.framework.Serializers
import com.jetbrains.rd.framework.SocketWire
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.put
import com.jetbrains.rd.util.threading.coroutines.nextTrueValueAsync
import com.jetbrains.rdclient.protocol.RdDispatcher
import com.jetbrains.rider.RiderEnvironment
import com.jetbrains.rider.aspire.generated.SessionModel
import com.jetbrains.rider.aspire.run.AspireHostConfiguration
import com.jetbrains.rider.aspire.sessionHost.SessionEvent
import com.jetbrains.rider.aspire.sessionHost.SessionExecutableFactory
import com.jetbrains.rider.aspire.sessionHost.SessionLogReceived
import com.jetbrains.rider.aspire.sessionHost.SessionStarted
import com.jetbrains.rider.aspire.sessionHost.SessionTerminated
import com.jetbrains.rider.aspire.sessionHost.findBySessionProject
import com.jetbrains.rider.aspire.sessionHost.hotReload.AspireProjectHotReloadConfigurationExtension
import com.jetbrains.rider.aspire.util.decodeAnsiCommandsToString
import com.jetbrains.rider.debugger.DebuggerWorkerProcessHandler
import com.jetbrains.rider.debugger.RiderDebugRunner.Companion.DEBUGGER_WORKER_LOG_CONF_ENV_KEY
import com.jetbrains.rider.debugger.RiderDebugRunner.Companion.DEBUGGER_WORKER_LOG_DIR_ENV_KEY
import com.jetbrains.rider.debugger.RiderDebugRunner.Companion.createLogSubDir
import com.jetbrains.rider.debugger.RiderDebuggerWorkerModelManager
import com.jetbrains.rider.debugger.targets.DEBUGGER_WORKER_LAUNCHER
import com.jetbrains.rider.model.debuggerWorker.DebugKind
import com.jetbrains.rider.model.debuggerWorker.DebuggerWorkerModel
import com.jetbrains.rider.model.debuggerWorker.DotNetCoreExeStartInfo
import com.jetbrains.rider.model.debuggerWorker.DotNetCoreInfo
import com.jetbrains.rider.model.debuggerWorker.DotNetDebuggerSessionModel
import com.jetbrains.rider.model.debuggerWorker.EncInfo
import com.jetbrains.rider.model.debuggerWorkerConnectionHelperModel
import com.jetbrains.rider.model.runnableProjectsModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.run.ConsoleKind
import com.jetbrains.rider.run.DebugProfileStateBase
import com.jetbrains.rider.run.TerminalProcessHandler
import com.jetbrains.rider.run.bindToSettings
import com.jetbrains.rider.run.configurations.RunnableProjectKinds
import com.jetbrains.rider.run.configurations.RuntimeHotReloadRunConfigurationInfo
import com.jetbrains.rider.run.configurations.launchSettings.LaunchSettingsJsonService
import com.jetbrains.rider.run.createConsole
import com.jetbrains.rider.run.createRunCommandLine
import com.jetbrains.rider.run.environment.ExecutableType
import com.jetbrains.rider.run.pid
import com.jetbrains.rider.run.toModelMap
import com.jetbrains.rider.runtime.DotNetExecutable
import com.jetbrains.rider.runtime.DotNetRuntime
import com.jetbrains.rider.runtime.RiderDotNetActiveRuntimeHost
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntime
import com.jetbrains.rider.util.NetUtils
import kotlinx.coroutines.flow.MutableSharedFlow
import java.nio.file.Path

abstract class BaseProjectSessionProcessLauncher : SessionProcessLauncherExtension {
    companion object {
        private val LOG = logger<BaseProjectSessionProcessLauncher>()
    }

    protected abstract val hotReloadExtension: AspireProjectHotReloadConfigurationExtension

    protected suspend fun getDotNetExecutable(
        sessionModel: SessionModel,
        hostRunConfiguration: AspireHostConfiguration?,
        project: Project
    ): Pair<DotNetExecutable, StartBrowserSettings?>? {
        val factory = SessionExecutableFactory.getInstance(project)
        val executable = factory.createExecutable(sessionModel, hostRunConfiguration)
        if (executable == null) {
            LOG.warn("Unable to create executable for project: ${sessionModel.projectPath}")
        }

        return executable
    }

    protected fun getDotNetRuntime(executable: DotNetExecutable, project: Project): DotNetCoreRuntime? {
        val runtime = DotNetRuntime.detectRuntimeForProject(
            project,
            RunnableProjectKinds.DotNetCore,
            RiderDotNetActiveRuntimeHost.getInstance(project),
            executable.runtimeType,
            executable.exePath,
            executable.projectTfm
        )?.runtime as? DotNetCoreRuntime
        if (runtime == null) {
            LOG.warn("Unable to detect runtime for executable: ${executable.exePath}")
        }

        return runtime
    }

    protected fun getDotNetCoreExeStartInfo(executable: DotNetExecutable, runtime: DotNetCoreRuntime) =
        DotNetCoreExeStartInfo(
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

    protected fun createPresentableCommandLine(executable: DotNetExecutable, runtime: DotNetCoreRuntime) = buildString {
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

    protected suspend fun enableHotReload(
        executable: DotNetExecutable,
        sessionProjectPath: Path,
        launchProfile: String?,
        lifetime: Lifetime,
        project: Project
    ): Pair<DotNetExecutable, ProcessAdapter?> {
        val runnableProject = project.solution.runnableProjectsModel.findBySessionProject(sessionProjectPath)
            ?: return executable to null

        val hotReloadRunInfo = RuntimeHotReloadRunConfigurationInfo(
            DefaultRunExecutor.EXECUTOR_ID,
            project,
            runnableProject,
            executable.projectTfm,
            null
        )

        val profile = if (!launchProfile.isNullOrEmpty()) {
            val launchSettings = readAction {
                LaunchSettingsJsonService.loadLaunchSettings(runnableProject)
            }
            launchSettings?.let { ls ->
                ls.profiles?.get(launchProfile)
            }
        } else null

        if (!hotReloadExtension.canExecute(lifetime, hotReloadRunInfo, profile)) {
            return executable to null
        }

        return hotReloadExtension.execute(executable, lifetime, project)
    }

    protected fun createRunProcessHandler(
        sessionId: String,
        dotnetExecutable: DotNetExecutable,
        dotnetRuntime: DotNetCoreRuntime,
        hotReloadProcessListener: ProcessAdapter?,
        sessionProcessLifetime: Lifetime,
        sessionEvents: MutableSharedFlow<SessionEvent>,
        project: Project,
        sessionProcessHandlerTerminated: (Int, String?) -> Unit
    ): TerminalProcessHandler {
        val commandLine = dotnetExecutable.createRunCommandLine(dotnetRuntime)
        val handler = TerminalProcessHandler(project, commandLine, commandLine.commandLineString)

        handler.addProcessListener(object : ProcessAdapter() {
            override fun processTerminated(event: ProcessEvent) {
                sessionProcessHandlerTerminated(event.exitCode, event.text)
            }
        })

        hotReloadProcessListener?.let { handler.addProcessListener(it) }

        sessionProcessLifetime.onTermination {
            if (!handler.isProcessTerminating && !handler.isProcessTerminated) {
                LOG.trace("Killing run session process handler (id: $sessionId)")
                handler.killProcess()
            }
        }

        subscribeToSessionEvents(sessionId, handler, sessionEvents)

        return handler
    }

    protected suspend fun prepareDebuggerWorkerSession(
        sessionId: String,
        debuggerSessionId: Long,
        dotnetExecutable: DotNetExecutable,
        dotnetRuntime: DotNetCoreRuntime,
        sessionEvents: MutableSharedFlow<SessionEvent>,
        sessionProcessLifetime: Lifetime,
        project: Project,
        modifyDebuggerWorkerCmd: (GeneralCommandLine) -> Unit,
        sessionProcessHandlerTerminated: (Int, String?) -> Unit
    ): DebuggerWorkerSession {
        val frontendToDebuggerPort = NetUtils.findFreePort(57200)
        val backendToDebuggerPort = NetUtils.findFreePort(57300)

        val (wire, protocol) = createDebuggerWorkerProtocol(frontendToDebuggerPort, sessionProcessLifetime)

        val startInfo = getDotNetCoreExeStartInfo(dotnetExecutable, dotnetRuntime)
        val debuggerSessionModel = DotNetDebuggerSessionModel(startInfo)
        debuggerSessionModel.sessionProperties.bindToSettings(sessionProcessLifetime, project).apply {
            debugKind.set(DebugKind.Live)
            remoteDebug.set(false)
            enableHeuristicPathResolve.set(false)
            editAndContinueEnabled.set(true)
        }

        val debuggerWorkerModel = RiderDebuggerWorkerModelManager.createDebuggerModel(sessionProcessLifetime, protocol)
        debuggerWorkerModel.activeSession.set(debuggerSessionModel)

        val presentableCommandLine = createPresentableCommandLine(dotnetExecutable, dotnetRuntime)
        val debuggerWorkerProcessHandler = createDebuggerWorkerProcessHandler(
            sessionId,
            frontendToDebuggerPort,
            backendToDebuggerPort,
            debuggerWorkerModel,
            presentableCommandLine,
            sessionProcessLifetime,
            sessionEvents,
            project,
            modifyDebuggerWorkerCmd,
            sessionProcessHandlerTerminated
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

        return DebuggerWorkerSession(
            debuggerSessionId,
            debuggerWorkerProcessHandler,
            console,
            protocol,
            debuggerSessionModel
        )
    }

    private fun createDebuggerWorkerProtocol(
        frontendToDebuggerPort: Int,
        sessionProcessLifetime: Lifetime,
    ): Pair<SocketWire.Server, Protocol> {
        val dispatcher = RdDispatcher(sessionProcessLifetime)
        val wire = SocketWire.Server(
            sessionProcessLifetime,
            dispatcher,
            port = frontendToDebuggerPort,
            optId = "FrontendToDebugWorker"
        )
        val protocol = Protocol(
            "FrontendToDebuggerWorker",
            Serializers(),
            Identities(IdKind.Server),
            dispatcher,
            wire,
            sessionProcessLifetime
        )

        return wire to protocol
    }

    private fun createDebuggerWorkerProcessHandler(
        sessionId: String,
        frontendToDebuggerPort: Int,
        backendToDebuggerPort: Int,
        debuggerWorkerModel: DebuggerWorkerModel,
        presentableCommandLine: String,
        sessionProcessLifetime: Lifetime,
        sessionEvents: MutableSharedFlow<SessionEvent>,
        project: Project,
        modifyDebuggerWorkerCmd: (GeneralCommandLine) -> Unit,
        sessionProcessHandlerTerminated: (Int, String?) -> Unit
    ): DebuggerWorkerProcessHandler {
        val debuggerWorkerLauncher = DEBUGGER_WORKER_LAUNCHER.getLauncher()
        val debuggerWorkerCmd = DebugProfileStateBase.createWorkerCmdForLauncherInfo(
            ConsoleKind.Normal,
            frontendToDebuggerPort,
            debuggerWorkerLauncher,
            ExecutableType.Console,
            true,
            false,
            true,
            "--backend-port=${backendToDebuggerPort}"
        ).also {
            modifyDebuggerWorkerCmd(it)
        }

        val debuggerWorkerLogDir = createLogSubDir(project, "DebuggerWorker", "JetBrains.Debugger.Worker")
        debuggerWorkerCmd.withEnvironment(DEBUGGER_WORKER_LOG_DIR_ENV_KEY, debuggerWorkerLogDir.absolutePath)
        debuggerWorkerCmd.withEnvironment(DEBUGGER_WORKER_LOG_CONF_ENV_KEY, RiderEnvironment.logBackendConf)
        debuggerWorkerCmd.withEnvironment("JET_DEBUGGER_PARENT_PROCESS_PID", ProcessHandle.current().pid().toString())
        if (BundleBase.SHOW_LOCALIZED_MESSAGES) {
            debuggerWorkerCmd.withEnvironment("JET_I18N_DEBUG", "true")
        }
        debuggerWorkerCmd.environment["DOTNET_gcServer"] = "0"

        val handler = TerminalProcessHandler(project, debuggerWorkerCmd, presentableCommandLine, false)

        val debuggerWorkerProcessHandler = DebuggerWorkerProcessHandler(
            handler,
            debuggerWorkerModel,
            false,
            "",
            sessionProcessLifetime
        )

        debuggerWorkerProcessHandler.addProcessListener(object : ProcessAdapter() {
            override fun processTerminated(event: ProcessEvent) {
                sessionProcessHandlerTerminated(event.exitCode, event.text)
            }
        })

        sessionProcessLifetime.onTermination {
            if (!debuggerWorkerProcessHandler.isProcessTerminating && !debuggerWorkerProcessHandler.isProcessTerminated) {
                LOG.trace("Killing debugger worker session process handler (id: $sessionId)")
                debuggerWorkerProcessHandler.destroyProcess()
            }
        }

        subscribeToSessionEvents(sessionId, debuggerWorkerProcessHandler.debuggerWorkerRealHandler, sessionEvents)

        return debuggerWorkerProcessHandler
    }

    private fun subscribeToSessionEvents(
        sessionId: String,
        handler: ProcessHandler,
        sessionEvents: MutableSharedFlow<SessionEvent>
    ) {
        handler.addProcessListener(object : ProcessAdapter() {
            override fun startNotified(event: ProcessEvent) {
                LOG.trace("Aspire session process started (id: $sessionId)")
                val pid = when (event.processHandler) {
                    is KillableProcessHandler -> event.processHandler.pid()
                    else -> null
                }
                if (pid == null) {
                    LOG.warn("Unable to determine process id for the session $sessionId")
                    sessionEvents.tryEmit(SessionTerminated(sessionId, -1))
                } else {
                    sessionEvents.tryEmit(SessionStarted(sessionId, pid))
                }
            }

            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                val text = decodeAnsiCommandsToString(event.text, outputType)
                val isStdErr = outputType == ProcessOutputType.STDERR
                sessionEvents.tryEmit(SessionLogReceived(sessionId, isStdErr, text))
            }

            override fun processNotStarted() {
                LOG.warn("Aspire session process is not started")
                sessionEvents.tryEmit(SessionTerminated(sessionId, -1))
            }
        })
    }

    protected fun startBrowser(
        hostRunConfiguration: AspireHostConfiguration?,
        startBrowserSettings: StartBrowserSettings?,
        processHandler: ProcessHandler
    ) {
        if (hostRunConfiguration == null || startBrowserSettings == null) return

        BrowserStarter(hostRunConfiguration, startBrowserSettings, processHandler).start()
    }

    data class DebuggerWorkerSession(
        val debuggerSessionId: Long,
        val debuggerWorkerProcessHandler: DebuggerWorkerProcessHandler,
        val console: ConsoleView,
        val protocol: Protocol,
        val debugSessionModel: DotNetDebuggerSessionModel
    )
}