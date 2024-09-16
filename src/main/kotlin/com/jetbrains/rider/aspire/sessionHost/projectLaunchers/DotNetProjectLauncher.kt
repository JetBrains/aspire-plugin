package com.jetbrains.rider.aspire.sessionHost.projectLaunchers

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.KillableProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutputType
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
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
import com.jetbrains.rider.aspire.generated.SessionModel
import com.jetbrains.rider.aspire.sessionHost.SessionEvent
import com.jetbrains.rider.aspire.sessionHost.SessionExecutableFactory
import com.jetbrains.rider.aspire.sessionHost.SessionHotReloadConfigurationExtension
import com.jetbrains.rider.aspire.sessionHost.SessionLogReceived
import com.jetbrains.rider.aspire.sessionHost.SessionManager
import com.jetbrains.rider.aspire.sessionHost.SessionStarted
import com.jetbrains.rider.aspire.sessionHost.SessionTerminated
import com.jetbrains.rider.aspire.sessionHost.findBySessionProject
import com.jetbrains.rider.aspire.util.decodeAnsiCommandsToString
import com.jetbrains.rider.debugger.DebuggerWorkerProcessHandler
import com.jetbrains.rider.debugger.RiderDebuggerWorkerModelManager
import com.jetbrains.rider.debugger.createAndStartSession
import com.jetbrains.rider.debugger.editAndContinue.DotNetRunHotReloadProcess
import com.jetbrains.rider.debugger.editAndContinue.hotReloadManager
import com.jetbrains.rider.debugger.targets.DEBUGGER_WORKER_LAUNCHER
import com.jetbrains.rider.model.debuggerWorker.DebugKind
import com.jetbrains.rider.model.debuggerWorker.DebuggerStartInfoBase
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
import com.jetbrains.rider.run.IDebuggerOutputListener
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
import icons.RiderIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.nameWithoutExtension

@Service(Service.Level.PROJECT)
class DotNetProjectLauncher(private val project: Project) {
    companion object {
        fun getInstance(project: Project) = project.service<DotNetProjectLauncher>()

        private val LOG = logger<DotNetProjectLauncher>()

        private const val DOTNET_HOTRELOAD_NAMEDPIPE_NAME = "DOTNET_HOTRELOAD_NAMEDPIPE_NAME"
    }

    suspend fun launchRunSession(
        sessionId: String,
        sessionModel: SessionModel,
        sessionLifetime: Lifetime,
        sessionEvents: MutableSharedFlow<SessionEvent>
    ) {
        val executable = getExecutable(sessionModel) ?: return
        val runtime = getRuntime(executable) ?: return

        LOG.trace { "Starting run session for project ${sessionModel.projectPath}" }

        val sessionProjectPath = Path(sessionModel.projectPath)
        val executableToRun = modifyExecutableToRun(
            executable,
            sessionProjectPath,
            sessionModel.launchProfile,
            sessionLifetime
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

        addHotReloadListener(handler, sessionLifetime, commandLine.environment)

        subscribeToSessionEvents(sessionId, handler, sessionEvents)

        handler.startNotify()
    }

    private suspend fun modifyExecutableToRun(
        executable: DotNetExecutable,
        sessionProjectPath: Path,
        launchProfile: String?,
        lifetime: Lifetime
    ): DotNetExecutable {
        val runnableProject = project.solution.runnableProjectsModel.findBySessionProject(sessionProjectPath)
            ?: return executable

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

        val ext = SessionHotReloadConfigurationExtension()
        if (!ext.canExecute(lifetime, hotReloadRunInfo, profile)) {
            return executable
        }

        return ext.execute(executable)
    }

    private fun addHotReloadListener(handler: KillableProcessHandler, lifetime: Lifetime, envs: Map<String, String>) {
        val pipeName = envs[DOTNET_HOTRELOAD_NAMEDPIPE_NAME]
        if (pipeName.isNullOrEmpty()) return

        handler.addProcessListener(object : ProcessAdapter() {
            override fun startNotified(event: ProcessEvent) {
                val runSession = DotNetRunHotReloadProcess(lifetime, pipeName)
                project.hotReloadManager.addProcess(runSession)
            }
        })
    }

    suspend fun launchDebugSession(
        sessionId: String,
        sessionModel: SessionModel,
        sessionLifetime: Lifetime,
        sessionEvents: MutableSharedFlow<SessionEvent>
    ) {
        val executable = getExecutable(sessionModel) ?: return
        val runtime = getRuntime(executable) ?: return

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
                sessionLifetime
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
        sessionLifetime: Lifetime
    ) {
        val debuggerSessionId = ExecutionEnvironment.getNextUnusedExecutionId()
        val frontendToDebuggerPort = NetUtils.findFreePort(57200)
        val backendToDebuggerPort = NetUtils.findFreePort(57300)

        val dispatcher = RdDispatcher(sessionLifetime)
        val wire = SocketWire.Server(
            sessionLifetime,
            dispatcher,
            port = frontendToDebuggerPort,
            optId = "FrontendToDebugWorker"
        )

        val sessionModel = DotNetDebuggerSessionModel(startInfo)
        sessionModel.sessionProperties.bindToSettings(sessionLifetime, project).apply {
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
            sessionLifetime
        )

        val workerModel = RiderDebuggerWorkerModelManager.createDebuggerModel(sessionLifetime, protocol)
        workerModel.activeSession.set(sessionModel)

        val debuggerWorkerProcessHandler = createDebuggerWorkerProcessHandler(
            sessionId,
            presentableCommandLine,
            frontendToDebuggerPort,
            backendToDebuggerPort,
            workerModel,
            sessionLifetime,
            sessionEvents
        )
        val console = createConsole(
            ConsoleKind.Normal,
            debuggerWorkerProcessHandler.debuggerWorkerRealHandler,
            project
        )

        wire.connected.nextTrueValueAsync(sessionLifetime).await()
        project.solution.debuggerWorkerConnectionHelperModel.ports.put(
            sessionLifetime,
            debuggerSessionId,
            backendToDebuggerPort
        )

        debuggerWorkerProcessHandler.addProcessListener(object : ProcessAdapter() {
            override fun processTerminated(event: ProcessEvent) {
                SessionManager.getInstance(project).sessionProcessWasTerminated(sessionId, event.exitCode, event.text)
            }
        })

        sessionLifetime.onTermination {
            if (!debuggerWorkerProcessHandler.isProcessTerminating && !debuggerWorkerProcessHandler.isProcessTerminated) {
                LOG.trace("Killing session process handler (id: $sessionId)")
                debuggerWorkerProcessHandler.destroyProcess()
            }
        }

        createAndStartSession(
            console,
            null,
            project,
            sessionLifetime,
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
        sessionEvents: MutableSharedFlow<SessionEvent>
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

    private suspend fun getExecutable(sessionModel: SessionModel): DotNetExecutable? {
        val factory = SessionExecutableFactory.getInstance(project)
        val executable = factory.createExecutable(sessionModel)
        if (executable == null) {
            LOG.warn("Unable to create executable for project: ${sessionModel.projectPath}")
        }

        return executable
    }

    private fun getRuntime(executable: DotNetExecutable): DotNetCoreRuntime? {
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
}