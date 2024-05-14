@file:Suppress("LoggingSimilarMessage")

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
import com.jetbrains.rd.util.reactive.hasTrueValue
import com.jetbrains.rd.util.threading.coroutines.nextTrueValueAsync
import com.jetbrains.rdclient.protocol.RdDispatcher
import com.jetbrains.rider.RiderEnvironment
import com.jetbrains.rider.RiderEnvironment.createRunCmdForLauncherInfo
import com.jetbrains.rider.debugger.DebuggerWorkerProcessHandler
import com.jetbrains.rider.debugger.RiderDebuggerWorkerModelManager
import com.jetbrains.rider.debugger.createAndStartSession
import com.jetbrains.rider.debugger.editAndContinue.DotNetRunHotReloadProcess
import com.jetbrains.rider.debugger.editAndContinue.hotReloadManager
import com.jetbrains.rider.debugger.targets.DEBUGGER_WORKER_LAUNCHER
import com.jetbrains.rider.hotReload.HotReloadHost
import com.jetbrains.rider.model.HotReloadSupportedInfo
import com.jetbrains.rider.model.debuggerWorker.*
import com.jetbrains.rider.model.debuggerWorkerConnectionHelperModel
import com.jetbrains.rider.model.runnableProjectsModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.run.*
import com.jetbrains.rider.run.configurations.RunnableProjectKinds
import com.jetbrains.rider.run.configurations.launchSettings.LaunchSettingsJsonService
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
import me.rafaelldi.aspire.util.decodeAnsiCommandsToString
import org.jetbrains.annotations.Nls
import java.nio.file.Path
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.nameWithoutExtension

@Service(Service.Level.PROJECT)
class SessionLauncher(private val project: Project) {
    companion object {
        fun getInstance(project: Project) = project.service<SessionLauncher>()

        private val LOG = logger<SessionLauncher>()

        private const val DOTNET_MODIFIABLE_ASSEMBLIES = "DOTNET_MODIFIABLE_ASSEMBLIES"
        private const val DOTNET_HOTRELOAD_NAMEDPIPE_NAME = "DOTNET_HOTRELOAD_NAMEDPIPE_NAME"
        private const val DOTNET_STARTUP_HOOKS = "DOTNET_STARTUP_HOOKS"
        private const val OTEL_EXPORTER_OTLP_ENDPOINT = "OTEL_EXPORTER_OTLP_ENDPOINT"

        private fun getOtlpEndpoint(port: Int) = "http://localhost:$port"
    }

    suspend fun launchSession(
        sessionId: String,
        sessionModel: SessionModel,
        processLifetime: Lifetime,
        sessionEvents: MutableSharedFlow<SessionEvent>,
        debuggingMode: Boolean,
        openTelemetryPort: Int?
    ) {
        LOG.info("Starting a session for the project ${sessionModel.projectPath}")

        if (processLifetime.isNotAlive) {
            LOG.warn("Unable to run project ${sessionModel.projectPath} because lifetimes are not alive")
            return
        }

        val factory = SessionExecutableFactory.getInstance(project)
        val executable = factory.createExecutable(sessionModel)
        if (executable == null) {
            LOG.warn("Unable to create executable for $sessionId (project: ${sessionModel.projectPath})")
            return
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
            return
        }

        val sessionProjectPath = Path(sessionModel.projectPath)
        if (debuggingMode || sessionModel.debug) {
            launchDebugSession(
                sessionId,
                sessionProjectPath.nameWithoutExtension,
                executable,
                runtime,
                openTelemetryPort,
                processLifetime,
                sessionEvents
            )
        } else {
            launchRunSession(
                sessionId,
                sessionProjectPath,
                executable,
                runtime,
                sessionModel.launchProfile?.firstOrNull(),
                openTelemetryPort,
                processLifetime,
                sessionEvents
            )
        }
    }

    private suspend fun launchRunSession(
        sessionId: String,
        sessionProjectPath: Path,
        executable: DotNetExecutable,
        runtime: DotNetCoreRuntime,
        launchProfile: String?,
        openTelemetryPort: Int?,
        processLifetime: Lifetime,
        sessionEvents: MutableSharedFlow<SessionEvent>
    ) {
        LOG.trace("Starting the session in the run mode")

        val executableToRun = modifyExecutableToRun(
            executable,
            sessionProjectPath,
            launchProfile,
            openTelemetryPort,
            processLifetime
        )

        val commandLine = executableToRun.createRunCommandLine(runtime)
        val handler = TerminalProcessHandler(project, commandLine, commandLine.commandLineString)

        handler.addProcessListener(object : ProcessAdapter() {
            override fun processTerminated(event: ProcessEvent) {
                SessionManager.getInstance(project).sessionProcessWasTerminated(sessionId, event.exitCode, event.text)
            }
        })

        processLifetime.onTermination {
            if (!handler.isProcessTerminating && !handler.isProcessTerminated) {
                LOG.trace("Killing session process handler (id: $sessionId)")
                handler.killProcess()
            }
        }

        addHotReloadListener(handler, processLifetime, commandLine.environment)

        subscribeToSessionEvents(sessionId, handler, sessionEvents)

        handler.startNotify()
    }

    private suspend fun modifyExecutableToRun(
        executable: DotNetExecutable,
        sessionProjectPath: Path,
        launchProfile: String?,
        openTelemetryPort: Int?,
        lifetime: Lifetime
    ): DotNetExecutable {
        val envs = executable.environmentVariables.toMutableMap()

        val isHotReloadAvailable = isHotReloadAvailable(executable, sessionProjectPath, launchProfile, lifetime)
        if (isHotReloadAvailable) {
            val pipeName = UUID.randomUUID().toString()
            envs[DOTNET_MODIFIABLE_ASSEMBLIES] = "debug"
            envs[DOTNET_HOTRELOAD_NAMEDPIPE_NAME] = pipeName
            val deltaApplierPath =
                RiderEnvironment.getBundledFile("JetBrains.Microsoft.Extensions.DotNetDeltaApplier.dll").absolutePath
            envs[DOTNET_STARTUP_HOOKS] = deltaApplierPath
        }

        val isOpenTelemetryAvailable = openTelemetryPort != null
        if (isOpenTelemetryAvailable) {
            envs[OTEL_EXPORTER_OTLP_ENDPOINT] = getOtlpEndpoint(requireNotNull(openTelemetryPort))
        }

        return if (isHotReloadAvailable || isOpenTelemetryAvailable) {
            executable.copy(environmentVariables = envs)
        } else {
            executable
        }
    }

    private suspend fun isHotReloadAvailable(
        executable: DotNetExecutable,
        sessionProjectPath: Path,
        launchProfile: String?,
        lifetime: Lifetime
    ): Boolean {
        if (executable.projectTfm == null) return false

        val hotReloadHost = HotReloadHost.getInstance(project)
        if (!hotReloadHost.runtimeHotReloadEnabled.hasTrueValue) return false

        val runnableProject =
            project.solution.runnableProjectsModel.findBySessionProject(sessionProjectPath) ?: return false

        if (launchProfile != null) {
            val launchSettings = LaunchSettingsJsonService.loadLaunchSettings(runnableProject)
            if (launchSettings != null) {
                val profile = launchSettings.profiles?.get(launchProfile)
                if (profile?.hotReloadEnabled == false) return false
            }
        }

        val info = HotReloadSupportedInfo(runnableProject.name, requireNotNull(executable.projectTfm).presentableName)
        return withContext(Dispatchers.EDT) {
            hotReloadHost.checkProjectConfigRuntimeHotReload(lifetime, info)
        }
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

    private suspend fun launchDebugSession(
        sessionId: String,
        @Nls sessionName: String,
        executable: DotNetExecutable,
        runtime: DotNetCoreRuntime,
        openTelemetryPort: Int?,
        processLifetime: Lifetime,
        sessionEvents: MutableSharedFlow<SessionEvent>
    ) {
        LOG.trace("Starting the session in the debug mode")

        val executableToDebug = modifyExecutableToDebug(executable, openTelemetryPort)

        val startInfo = DotNetCoreExeStartInfo(
            DotNetCoreInfo(runtime.cliExePath),
            executableToDebug.projectTfm?.let { EncInfo(it) },
            executableToDebug.exePath,
            executableToDebug.workingDirectory,
            executableToDebug.programParameterString,
            executableToDebug.environmentVariables.toModelMap,
            executableToDebug.runtimeArguments,
            executableToDebug.executeAsIs,
            executableToDebug.useExternalConsole
        )

        withContext(Dispatchers.EDT) {
            createAndStartDebugSession(
                sessionId,
                sessionName,
                startInfo,
                sessionEvents,
                processLifetime
            )
        }
    }

    private fun modifyExecutableToDebug(
        executable: DotNetExecutable,
        openTelemetryPort: Int?
    ): DotNetExecutable {
        val envs = executable.environmentVariables.toMutableMap()

        val isOpenTelemetryAvailable = openTelemetryPort != null
        if (isOpenTelemetryAvailable) {
            envs[OTEL_EXPORTER_OTLP_ENDPOINT] = getOtlpEndpoint(requireNotNull(openTelemetryPort))
        }

        return if (isOpenTelemetryAvailable) {
            executable.copy(environmentVariables = envs)
        } else {
            executable
        }
    }

    private suspend fun createAndStartDebugSession(
        sessionId: String,
        @Nls sessionName: String,
        startInfo: DebuggerStartInfoBase,
        sessionEvents: MutableSharedFlow<SessionEvent>,
        processLifetime: Lifetime
    ) {
        val debuggerSessionId = ExecutionEnvironment.getNextUnusedExecutionId()
        val frontendToDebuggerPort = NetUtils.findFreePort(57200)
        val backendToDebuggerPort = NetUtils.findFreePort(57300)

        val dispatcher = RdDispatcher(processLifetime)
        val wire = SocketWire.Server(
            processLifetime,
            dispatcher,
            port = frontendToDebuggerPort,
            optId = "FrontendToDebugWorker"
        )

        val sessionModel = DotNetDebuggerSessionModel(startInfo)
        sessionModel.sessionProperties.bindToSettings(processLifetime, project).apply {
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
            processLifetime
        )

        val workerModel = RiderDebuggerWorkerModelManager.createDebuggerModel(processLifetime, protocol)
        workerModel.activeSession.set(sessionModel)

        val debuggerWorkerProcessHandler = createDebuggerWorkerProcessHandler(
            sessionId,
            frontendToDebuggerPort,
            backendToDebuggerPort,
            workerModel,
            processLifetime,
            sessionEvents
        )
        val console = createConsole(
            ConsoleKind.Normal,
            debuggerWorkerProcessHandler.debuggerWorkerRealHandler,
            project
        )

        wire.connected.nextTrueValueAsync(processLifetime).await()
        project.solution.debuggerWorkerConnectionHelperModel.ports.put(
            processLifetime,
            debuggerSessionId,
            backendToDebuggerPort
        )

        debuggerWorkerProcessHandler.addProcessListener(object : ProcessAdapter() {
            override fun processTerminated(event: ProcessEvent) {
                SessionManager.getInstance(project).sessionProcessWasTerminated(sessionId, event.exitCode, event.text)
            }
        })

        processLifetime.onTermination {
            if (!debuggerWorkerProcessHandler.isProcessTerminating && !debuggerWorkerProcessHandler.isProcessTerminated) {
                LOG.trace("Killing session process handler (id: $sessionId)")
                debuggerWorkerProcessHandler.destroyProcess()
            }
        }

        createAndStartSession(
            console,
            null,
            project,
            processLifetime,
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
        processLifetime: Lifetime,
        sessionEvents: MutableSharedFlow<SessionEvent>
    ): DebuggerWorkerProcessHandler {
        val launcher = DEBUGGER_WORKER_LAUNCHER.getLauncher()
        val commandLine = createRunCmdForLauncherInfo(
            launcher,
            "--mode=client",
            "--frontend-port=${frontendToDebuggerPort}",
            "--backend-port=${backendToDebuggerPort}"
        )
        val handler = TerminalProcessHandler(project, commandLine, commandLine.commandLineString, false)

        val debuggerWorkerProcessHandler = DebuggerWorkerProcessHandler(
            handler,
            workerModel,
            false,
            commandLine.commandLineString,
            processLifetime
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