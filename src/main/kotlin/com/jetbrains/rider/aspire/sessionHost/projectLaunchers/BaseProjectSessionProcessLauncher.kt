@file:Suppress("UnstableApiUsage")

package com.jetbrains.rider.aspire.sessionHost.projectLaunchers

import com.intellij.BundleBase
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.ide.browsers.BrowserStarter
import com.intellij.ide.browsers.StartBrowserSettings
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
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
import com.jetbrains.rider.aspire.sessionHost.SessionExecutableFactory
import com.jetbrains.rider.aspire.sessionHost.findBySessionProject
import com.jetbrains.rider.aspire.sessionHost.hotReload.AspireProjectHotReloadConfigurationExtension
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
import com.jetbrains.rider.run.createRunCommandLine
import com.jetbrains.rider.run.environment.ExecutableType
import com.jetbrains.rider.run.toModelMap
import com.jetbrains.rider.runtime.DotNetExecutable
import com.jetbrains.rider.runtime.DotNetRuntime
import com.jetbrains.rider.runtime.RiderDotNetActiveRuntimeHost
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntime
import com.jetbrains.rider.util.NetUtils
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

    protected fun createModelStartInfo(executable: DotNetExecutable, runtime: DotNetCoreRuntime) =
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
        sessionProcessEventListener: ProcessListener,
        sessionProcessTerminatedListener: ProcessListener,
        hotReloadProcessListener: ProcessListener?,
        sessionProcessLifetime: Lifetime,
        project: Project
    ): TerminalProcessHandler {
        val commandLine = dotnetExecutable.createRunCommandLine(dotnetRuntime)
        val handler = TerminalProcessHandler(project, commandLine, commandLine.commandLineString)

        handler.addProcessListener(sessionProcessEventListener)
        handler.addProcessListener(sessionProcessTerminatedListener)
        hotReloadProcessListener?.let { handler.addProcessListener(it) }

        sessionProcessLifetime.onTermination {
            if (!handler.isProcessTerminating && !handler.isProcessTerminated) {
                LOG.trace("Killing run session process handler (id: $sessionId)")
                handler.killProcess()
            }
        }

        return handler
    }

    protected suspend fun initDebuggerSession(
        sessionId: String,
        debuggerSessionId: Long,
        dotnetExecutable: DotNetExecutable,
        dotnetRuntime: DotNetCoreRuntime,
        sessionProcessEventListener: ProcessListener,
        sessionProcessTerminatedListener: ProcessListener,
        sessionProcessLifetime: Lifetime,
        project: Project,
        modifyDebuggerWorkerCmd: (GeneralCommandLine) -> Unit,
    ): DebuggerWorkerSession {
        val frontendToDebuggerPort = NetUtils.findFreePort(57200)
        val backendToDebuggerPort = NetUtils.findFreePort(57300)

        val (wire, protocol) = createDebuggerWorkerProtocol(frontendToDebuggerPort, sessionProcessLifetime)

        val presentableCommandLine = createPresentableCommandLine(dotnetExecutable, dotnetRuntime)
        val (debuggerWorkerModel, debuggerWorkerProcessHandler) = getDebuggerWorkerModelAndHandler(
            sessionId,
            debuggerSessionId,
            frontendToDebuggerPort,
            backendToDebuggerPort,
            wire,
            protocol,
            presentableCommandLine,
            sessionProcessEventListener,
            sessionProcessTerminatedListener,
            sessionProcessLifetime,
            project,
            modifyDebuggerWorkerCmd,
        )

        val startInfo = createModelStartInfo(dotnetExecutable, dotnetRuntime)

        val debuggerSessionModel = DotNetDebuggerSessionModel(startInfo)
        debuggerSessionModel.sessionProperties.bindToSettings(sessionProcessLifetime, project).apply {
            debugKind.set(DebugKind.Live)
            remoteDebug.set(false)
            enableHeuristicPathResolve.set(false)
            editAndContinueEnabled.set(true)
        }

        debuggerWorkerModel.activeSession.set(debuggerSessionModel)

        return DebuggerWorkerSession(
            debuggerWorkerProcessHandler,
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

    private suspend fun getDebuggerWorkerModelAndHandler(
        sessionId: String,
        debuggerSessionId: Long,
        frontendToDebuggerPort: Int,
        backendToDebuggerPort: Int,
        wire: SocketWire.Server,
        protocol: Protocol,
        presentableCommandLine: String,
        sessionProcessEventListener: ProcessListener,
        sessionProcessTerminatedListener: ProcessListener,
        sessionProcessLifetime: Lifetime,
        project: Project,
        modifyDebuggerWorkerCmd: (GeneralCommandLine) -> Unit
    ): Pair<DebuggerWorkerModel, DebuggerWorkerProcessHandler> {
        val debuggerWorkerCmd = DebugProfileStateBase.createWorkerCmdForLauncherInfo(
            ConsoleKind.Normal,
            frontendToDebuggerPort,
            DEBUGGER_WORKER_LAUNCHER.getLauncher(),
            ExecutableType.Console,
            true,
            false,
            true,
            "--backend-port=${backendToDebuggerPort}"
        ).also {
            modifyDebuggerWorkerCmd(it)
        }

        LOG.trace { "Created debugger worker command line: $debuggerWorkerCmd" }

        val debuggerWorkerLogDir = createLogSubDir(project, "DebuggerWorker", "JetBrains.Debugger.Worker")

        debuggerWorkerCmd.withEnvironment(DEBUGGER_WORKER_LOG_DIR_ENV_KEY, debuggerWorkerLogDir.absolutePath)
        debuggerWorkerCmd.withEnvironment(DEBUGGER_WORKER_LOG_CONF_ENV_KEY, RiderEnvironment.logBackendConf)
        debuggerWorkerCmd.withEnvironment("JET_DEBUGGER_PARENT_PROCESS_PID", ProcessHandle.current().pid().toString())
        debuggerWorkerCmd.withEnvironment("DOTNET_gcServer", "0")
        if (BundleBase.SHOW_LOCALIZED_MESSAGES) {
            debuggerWorkerCmd.withEnvironment("JET_I18N_DEBUG", "true")
        }

        val debuggerWorkerModel = RiderDebuggerWorkerModelManager.createDebuggerModel(sessionProcessLifetime, protocol)
        val handler = TerminalProcessHandler(project, debuggerWorkerCmd, presentableCommandLine, false)
        val debuggerWorkerProcessHandler = DebuggerWorkerProcessHandler(
            handler,
            debuggerWorkerModel,
            false,
            "",
            sessionProcessLifetime
        )

        debuggerWorkerProcessHandler.debuggerWorkerRealHandler.addProcessListener(sessionProcessEventListener)
        debuggerWorkerProcessHandler.addProcessListener(sessionProcessTerminatedListener)

        sessionProcessLifetime.onTermination {
            if (!debuggerWorkerProcessHandler.isProcessTerminating && !debuggerWorkerProcessHandler.isProcessTerminated) {
                LOG.trace("Killing debugger worker session process handler (id: $sessionId)")
                debuggerWorkerProcessHandler.destroyProcess()
            }
        }

        wire.connected.nextTrueValueAsync(sessionProcessLifetime).await()
        project.solution.debuggerWorkerConnectionHelperModel.ports.put(
            sessionProcessLifetime,
            debuggerSessionId,
            backendToDebuggerPort
        )

        return debuggerWorkerModel to debuggerWorkerProcessHandler
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
        val debuggerWorkerProcessHandler: DebuggerWorkerProcessHandler,
        val protocol: Protocol,
        val debugSessionModel: DotNetDebuggerSessionModel
    )
}