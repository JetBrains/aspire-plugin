package me.rafaelldi.aspire.sessionHost

import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.util.io.systemIndependentPath
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
import com.jetbrains.rider.model.RdTargetFrameworkId
import com.jetbrains.rider.model.debuggerWorker.*
import com.jetbrains.rider.model.debuggerWorkerConnectionHelperModel
import com.jetbrains.rider.model.runnableProjectsModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.run.*
import com.jetbrains.rider.run.configurations.RunnableProjectKinds
import com.jetbrains.rider.runtime.RiderDotNetActiveRuntimeHost
import com.jetbrains.rider.util.NetUtils
import icons.RiderIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import me.rafaelldi.aspire.generated.SessionModel
import me.rafaelldi.aspire.generated.SessionUpsertResult
import me.rafaelldi.aspire.settings.AspireSettings
import me.rafaelldi.aspire.util.MSBuildPropertyService
import me.rafaelldi.aspire.util.decodeAnsiCommandsToString
import org.jetbrains.annotations.Nls
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.nameWithoutExtension

@Service(Service.Level.PROJECT)
class AspireSessionLauncher(private val project: Project) {
    companion object {
        fun getInstance(project: Project) = project.service<AspireSessionLauncher>()

        private val LOG = logger<AspireSessionLauncher>()

        private const val OTEL_EXPORTER_OTLP_ENDPOINT = "OTEL_EXPORTER_OTLP_ENDPOINT"
        private fun getOtlpEndpoint(port: Int) = "http://localhost:$port"
    }

    suspend fun launchSession(
        sessionId: String,
        sessionModel: SessionModel,
        sessionLifetime: Lifetime,
        sessionEvents: MutableSharedFlow<AspireSessionEvent>,
        isHostDebug: Boolean,
        openTelemetryPort: Int
    ): SessionUpsertResult? {
        LOG.info("Starting a session for the project ${sessionModel.projectPath}")

        if (sessionLifetime.isNotAlive) {
            LOG.warn("Unable to run project ${sessionModel.projectPath} because lifetimes are not alive")
            return null
        }

        val executable = getExecutable(sessionModel)
        if (executable == null) {
            LOG.warn("Unable to find executable for $sessionId (project: ${sessionModel.projectPath})")
            return null
        }

        val isDebug = isHostDebug || sessionModel.debug
        if (isDebug) {
            launchDebugSession(
                sessionId,
                executable,
                sessionModel,
                sessionLifetime,
                sessionEvents,
                openTelemetryPort
            )
        } else {
            launchRunSession(
                sessionId,
                executable.first,
                sessionModel,
                sessionLifetime,
                sessionEvents,
                openTelemetryPort
            )
        }

        return SessionUpsertResult(sessionId)
    }

    private suspend fun getExecutable(sessionModel: SessionModel): Pair<Path, RdTargetFrameworkId?>? {
        val runnableProjects = project.solution.runnableProjectsModel.projects.valueOrNull ?: return null
        val sessionProjectPath = Path(sessionModel.projectPath)
        val sessionProjectPathString = sessionProjectPath.systemIndependentPath
        val runnableProject = runnableProjects.singleOrNull {
            it.projectFilePath == sessionProjectPathString && it.kind == RunnableProjectKinds.DotNetCore
        }
        if (runnableProject != null) {
            val output = runnableProject.projectOutputs.firstOrNull() ?: return null
            return Path(output.exePath) to output.tfm
        } else {
            val propertyService = MSBuildPropertyService.getInstance(project)
            return propertyService.getExecutableFromMSBuildProperties(sessionProjectPath)
        }
    }

    private fun launchRunSession(
        sessionId: String,
        executablePath: Path,
        sessionModel: SessionModel,
        sessionLifetime: Lifetime,
        sessionEvents: MutableSharedFlow<AspireSessionEvent>,
        openTelemetryPort: Int
    ) {
        val commandLine = getRunningCommandLine(executablePath, sessionModel, openTelemetryPort)

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

    private fun getRunningCommandLine(
        executablePath: Path,
        sessionModel: SessionModel,
        openTelemetryPort: Int
    ): GeneralCommandLine {
        val commandLine = FormatPreservingCommandLine()
            .withExePath(executablePath.absolutePathString())
            .withWorkDirectory(executablePath.parent.absolutePathString())

        if (sessionModel.args?.isNotEmpty() == true) {
            commandLine.withParameters(*sessionModel.args)
        }

        if (sessionModel.envs?.isNotEmpty() == true) {
            commandLine.withEnvironment(sessionModel.envs.associate { it.key to it.value })
        }

        if (AspireSettings.getInstance().collectTelemetry) {
            commandLine.withEnvironment(OTEL_EXPORTER_OTLP_ENDPOINT, getOtlpEndpoint(openTelemetryPort))
        }

        return commandLine
    }

    private suspend fun launchDebugSession(
        sessionId: String,
        executable: Pair<Path, RdTargetFrameworkId?>,
        sessionModel: SessionModel,
        sessionLifetime: Lifetime,
        sessionEvents: MutableSharedFlow<AspireSessionEvent>,
        openTelemetryPort: Int
    ) {
        val runtime = RiderDotNetActiveRuntimeHost.getInstance(project).dotNetCoreRuntime.value
        if (runtime == null) {
            LOG.warn("Unable to find dotnet runtime")
            return
        }

        val (executablePath, projectTfm) = executable
        val args = sessionModel.args?.joinToString(" ") ?: ""
        val envs = sessionModel.envs?.associate { it.key to it.value }?.toMutableMap() ?: mutableMapOf()
        if (AspireSettings.getInstance().collectTelemetry) {
            envs[OTEL_EXPORTER_OTLP_ENDPOINT] = getOtlpEndpoint(openTelemetryPort)
        }

        val startInfo = DotNetCoreExeStartInfo(
            DotNetCoreInfo(runtime.cliExePath),
            projectTfm?.let { EncInfo(it) },
            executablePath.absolutePathString(),
            executablePath.parent.absolutePathString(),
            args,
            envs.map { StringPair(it.key, it.value) }.toList(),
            null,
            true,
            false
        )

        val projectPath = Path(sessionModel.projectPath)
        withContext(Dispatchers.EDT) {
            createAndStartDebugSession(
                sessionId,
                projectPath.nameWithoutExtension,
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
        val frontendToDebuggerPort = NetUtils.findFreePort(67700)
        val backendToDebuggerPort = NetUtils.findFreePort(87700)
        val lifetimeDefinition = lifetime.createNested()

        val dispatcher = RdDispatcher(lifetimeDefinition)
        val wire = SocketWire.Server(
            lifetimeDefinition,
            dispatcher,
            port = frontendToDebuggerPort,
            optId = "FrontendToDebugWorker"
        )
        val protocol = Protocol(
            "FrontendToDebuggerWorker",
            Serializers(),
            Identities(IdKind.Client),
            dispatcher,
            wire,
            lifetimeDefinition
        )

        val workerModel = RiderDebuggerWorkerModelManager.createDebuggerModel(lifetimeDefinition, protocol)

        val debuggerWorkerProcessHandler = createDebuggerWorkerProcessHandler(
            sessionId,
            frontendToDebuggerPort,
            backendToDebuggerPort,
            workerModel,
            lifetimeDefinition.lifetime
        )
        subscribeToSessionEvents(
            sessionId,
            debuggerWorkerProcessHandler.debuggerWorkerRealHandler,
            sessionEvents
        )

        val debuggerSessionId = ExecutionEnvironment.getNextUnusedExecutionId()
        project.solution.debuggerWorkerConnectionHelperModel.ports.put(
            lifetimeDefinition,
            debuggerSessionId,
            backendToDebuggerPort
        )

        wire.connected.nextTrueValueAsync(lifetimeDefinition.lifetime).await()

        val sessionModel = DotNetDebuggerSessionModel(startInfo)
        sessionModel.sessionProperties.bindToSettings(lifetimeDefinition, project).apply {
            debugKind.set(DebugKind.Live)
            remoteDebug.set(false)
            enableHeuristicPathResolve.set(false)
            editAndContinueEnabled.set(true)
        }
        workerModel.activeSession.set(sessionModel)

        val console = createConsole(
            ConsoleKind.Normal,
            debuggerWorkerProcessHandler.debuggerWorkerRealHandler,
            project
        )
        val executionResult = DefaultExecutionResult(console, debuggerWorkerProcessHandler)

        createAndStartSession(
            executionResult.executionConsole,
            null,
            project,
            lifetimeDefinition.lifetime,
            executionResult.processHandler,
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
        sessionLifetime: Lifetime
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