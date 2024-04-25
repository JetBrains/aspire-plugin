package me.rafaelldi.aspire.sessionHost

import com.intellij.execution.process.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.jetbrains.rd.framework.*
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.isNotAlive
import com.jetbrains.rd.util.put
import com.jetbrains.rd.util.threading.coroutines.nextTrueValueAsync
import com.jetbrains.rdclient.protocol.RdDispatcher
import com.jetbrains.rider.RiderEnvironment.createRunCmdForLauncherInfo
import com.jetbrains.rider.debugger.DebuggerWorkerProcessHandler
import com.jetbrains.rider.debugger.RiderDebuggerWorkerModelManager
import com.jetbrains.rider.debugger.createAndStartSession
import com.jetbrains.rider.debugger.targets.DEBUGGER_WORKER_LAUNCHER
import com.jetbrains.rider.model.debuggerWorker.*
import com.jetbrains.rider.model.debuggerWorkerConnectionHelperModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.run.*
import com.jetbrains.rider.run.configurations.RunnableProjectKinds
import com.jetbrains.rider.runtime.DotNetExecutable
import com.jetbrains.rider.runtime.DotNetRuntime
import com.jetbrains.rider.runtime.RiderDotNetActiveRuntimeHost
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntime
import com.jetbrains.rider.util.NetUtils
import icons.RiderIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import me.rafaelldi.aspire.generated.SessionModel
import me.rafaelldi.aspire.generated.SessionUpsertResult
import me.rafaelldi.aspire.util.decodeAnsiCommandsToString
import org.jetbrains.annotations.Nls
import kotlin.io.path.Path
import kotlin.io.path.nameWithoutExtension

@Service(Service.Level.PROJECT)
class AspireSessionLauncher(private val project: Project) {
    companion object {
        fun getInstance(project: Project) = project.service<AspireSessionLauncher>()

        private val LOG = logger<AspireSessionLauncher>()
    }

    suspend fun launchSession(
        sessionId: String,
        sessionModel: SessionModel,
        sessionLifetime: Lifetime,
        sessionEvents: MutableSharedFlow<AspireSessionEvent>,
        debuggingMode: Boolean,
        openTelemetryPort: Int
    ): SessionUpsertResult? {
        LOG.info("Starting a session for the project ${sessionModel.projectPath}")

        if (sessionLifetime.isNotAlive) {
            LOG.warn("Unable to run project ${sessionModel.projectPath} because lifetimes are not alive")
            return null
        }

        val factory = SessionExecutableFactory.getInstance(project)
        val executable = factory.createExecutable(sessionModel, openTelemetryPort)
        if (executable == null) {
            LOG.warn("Unable to create executable for $sessionId (project: ${sessionModel.projectPath})")
            return null
        }
        val runtime = DotNetRuntime.detectRuntimeForProject(
            project,
            RunnableProjectKinds.DotNetCore,
            RiderDotNetActiveRuntimeHost.getInstance(project),
            executable.runtimeType,
            executable.exePath,
            executable.projectTfm
        )?.runtime as? DotNetCoreRuntime
        if (runtime == null) {
            LOG.warn("Unable to detect runtime for $sessionId (project: ${sessionModel.projectPath})")
            return null
        }

        if (debuggingMode || sessionModel.debug) {
            launchDebugSession(
                sessionId,
                Path(sessionModel.projectPath).nameWithoutExtension,
                executable,
                runtime,
                sessionLifetime,
                sessionEvents
            )
        } else {
            launchRunSession(
                sessionId,
                executable,
                runtime,
                sessionLifetime,
                sessionEvents
            )
        }

        return SessionUpsertResult(sessionId)
    }

    private fun launchRunSession(
        sessionId: String,
        executable: DotNetExecutable,
        runtime: DotNetCoreRuntime,
        sessionLifetime: Lifetime,
        sessionEvents: MutableSharedFlow<AspireSessionEvent>
    ) {
        val commandLine = executable.createRunCommandLine(runtime)
        val handler = KillableProcessHandler(commandLine)
        subscribeToSessionEvents(sessionId, handler, sessionEvents)

        sessionLifetime.onTermination {
            if (!handler.isProcessTerminating && !handler.isProcessTerminated) {
                LOG.trace("Killing session process (id: $sessionId)")
                handler.destroyProcess()
            }
        }

        handler.startNotify()
    }

    private suspend fun launchDebugSession(
        sessionId: String,
        @Nls sessionName: String,
        executable: DotNetExecutable,
        runtime: DotNetCoreRuntime,
        sessionLifetime: Lifetime,
        sessionEvents: MutableSharedFlow<AspireSessionEvent>
    ) {
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

        withContext(Dispatchers.EDT) {
            createAndStartDebugSession(
                sessionId,
                sessionName,
                startInfo,
                sessionEvents,
                sessionLifetime
            )
        }
    }

    private suspend fun createAndStartDebugSession(
        sessionId: String,
        @Nls sessionName: String,
        startInfo: DebuggerStartInfoBase,
        sessionEvents: MutableSharedFlow<AspireSessionEvent>,
        lifetime: Lifetime
    ) {
        val debuggerSessionId = ExecutionEnvironment.getNextUnusedExecutionId()
        val frontendToDebuggerPort = NetUtils.findFreePort(57200)
        val backendToDebuggerPort = NetUtils.findFreePort(57300)

        val lifetimeDefinition = lifetime.createNested()

        val dispatcher = RdDispatcher(lifetimeDefinition)
        val wire = SocketWire.Server(
            lifetimeDefinition,
            dispatcher,
            port = frontendToDebuggerPort,
            optId = "FrontendToDebugWorker"
        )

        val sessionModel = DotNetDebuggerSessionModel(startInfo)
        sessionModel.sessionProperties.bindToSettings(lifetimeDefinition, project).apply {
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
            lifetimeDefinition
        )

        val workerModel = RiderDebuggerWorkerModelManager.createDebuggerModel(lifetimeDefinition, protocol)
        workerModel.activeSession.set(sessionModel)

        val debuggerWorkerProcessHandler = createDebuggerWorkerProcessHandler(
            sessionId,
            frontendToDebuggerPort,
            backendToDebuggerPort,
            workerModel,
            lifetimeDefinition.lifetime,
            sessionEvents
        )
        val console = createConsole(
            ConsoleKind.Normal,
            debuggerWorkerProcessHandler.debuggerWorkerRealHandler,
            project
        )

        wire.connected.nextTrueValueAsync(lifetimeDefinition.lifetime).await()
        project.solution.debuggerWorkerConnectionHelperModel.ports.put(
            lifetimeDefinition,
            debuggerSessionId,
            backendToDebuggerPort
        )

        createAndStartSession(
            console,
            null,
            project,
            lifetimeDefinition.lifetime,
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
        frontendToDebuggerPort: Int,
        backendToDebuggerPort: Int,
        workerModel: DebuggerWorkerModel,
        sessionLifetime: Lifetime,
        sessionEvents: MutableSharedFlow<AspireSessionEvent>
    ): DebuggerWorkerProcessHandler {
        val launcher = DEBUGGER_WORKER_LAUNCHER.getLauncher()
        val commandLine = createRunCmdForLauncherInfo(
            launcher,
            "--mode=client",
            "--frontend-port=${frontendToDebuggerPort}",
            "--backend-port=${backendToDebuggerPort}"
        )
        val handler = TerminalProcessHandler(project, commandLine, commandLine.commandLineString, false)

        sessionLifetime.onTermination {
            if (!handler.isProcessTerminating && !handler.isProcessTerminated) {
                LOG.trace("Killing session process (id: $sessionId)")
                handler.killProcess()
            }
        }

        val debuggerWorkerProcessHandler = DebuggerWorkerProcessHandler(
            handler,
            workerModel,
            false,
            commandLine.commandLineString,
            sessionLifetime
        )

        subscribeToSessionEvents(
            sessionId,
            debuggerWorkerProcessHandler.debuggerWorkerRealHandler,
            sessionEvents
        )

        return debuggerWorkerProcessHandler
    }

    private fun subscribeToSessionEvents(
        sessionId: String,
        handler: ProcessHandler,
        sessionEvents: MutableSharedFlow<AspireSessionEvent>
    ) {
        handler.addProcessListener(object : ProcessAdapter() {
            override fun startNotified(event: ProcessEvent) {
                LOG.info("Aspire session process started (id: $sessionId)")
                val pid = when (event.processHandler) {
                    is KillableProcessHandler -> event.processHandler.pid()
                    else -> null
                }
                if (pid == null) {
                    LOG.warn("Unable to determine process id for the session $sessionId")
                    sessionEvents.tryEmit(AspireSessionTerminated(sessionId, -1))
                } else {
                    sessionEvents.tryEmit(AspireSessionStarted(sessionId, pid))
                }
            }

            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                val text = decodeAnsiCommandsToString(event.text, outputType)
                val isStdErr = outputType == ProcessOutputType.STDERR
                sessionEvents.tryEmit(AspireSessionLogReceived(sessionId, isStdErr, text))
            }

            override fun processTerminated(event: ProcessEvent) {
                LOG.info("Aspire session process terminated (id: $sessionId)")
                sessionEvents.tryEmit(AspireSessionTerminated(sessionId, event.exitCode))
            }

            override fun processNotStarted() {
                LOG.warn("Aspire session process is not started")
                sessionEvents.tryEmit(AspireSessionTerminated(sessionId, -1))
            }
        })
    }
}