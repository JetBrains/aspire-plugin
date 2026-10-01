@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.run.cli

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.ui.ConsoleView
import com.intellij.ide.BrowserUtil
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.platform.eel.*
import com.intellij.platform.eel.provider.*
import com.intellij.psi.search.ExecutionSearchScopes
import com.intellij.util.applyIf
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.io.BaseOutputReader
import com.jetbrains.aspire.AspireCoreBundle
import com.jetbrains.aspire.AspireService
import com.jetbrains.aspire.cli.AspireCliLocator
import com.jetbrains.aspire.cli.AspireCliLogLevel
import com.jetbrains.aspire.dcp.toDcpEnvironmentVariables
import com.jetbrains.aspire.extensions.AppHostDebuggerExtension
import com.jetbrains.aspire.extensions.DevCertificateProvider
import com.jetbrains.aspire.run.AspireExecutionListener.Companion.APP_HOST_ENVIRONMENT_KEY
import com.jetbrains.aspire.run.AsyncRunProfileState
import com.jetbrains.aspire.run.StoppedContainerRuntimeProcessListener
import com.jetbrains.aspire.settings.AspireSettings
import com.jetbrains.aspire.util.*
import com.jetbrains.aspire.worker.AspireAppHost.AppHostEnvironment
import com.jetbrains.aspire.worker.AspireWorker
import kotlinx.coroutines.launch
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.absolutePathString
import kotlin.io.path.name

internal class AspireCliRunProfileState(
    private val configuration: AspireCliRunConfiguration,
    private val environment: ExecutionEnvironment
) : AsyncRunProfileState {
    companion object {
        private val LOG = logger<AspireCliRunProfileState>()
        private const val NOTIFICATION_GROUP = "Aspire"
        private const val INSTALL_URL = "https://aspire.dev/get-started/install-cli/"
    }

    private val containerRuntimeNotificationCount = AtomicInteger()

    override suspend fun executeSuspending(executor: Executor, programRunner: ProgramRunner<*>): ExecutionResult {
        val options = configuration.cliOptions
        val project = environment.project

        val appHostFile = options.appHostFilePath?.takeIf { it.isNotBlank() }?.let { Path.of(it) }
        if (appHostFile == null) {
            throw ExecutionException(AspireCoreBundle.message("run.configuration.cli.error.no.app.host"))
        }

        val aspireCli = AspireCliLocator.getInstance(project).locate()?.asNioPath()
        if (aspireCli == null) {
            notifyCliNotInstalled()
            throw ExecutionException(AspireCoreBundle.message("run.configuration.cli.error.cli.not.found"))
        }

        val eelApi = project.getEelDescriptor().toEelApi()

        val envs = buildBaseEnvironment(options, eelApi).toMutableMap()
        putAdditionalEnvironmentVariables(envs, appHostFile)

        val debugServices = executor.id == DefaultDebugExecutor.EXECUTOR_ID
        val appHostDebugger = if (debugServices) resolveAppHostDebugger(appHostFile) else null
        configureDebuggingEnvironmentVariables(debugServices, appHostDebugger != null, envs)

        checkAndNotifyDevCertificate(envs)

        environment.putUserData(APP_HOST_ENVIRONMENT_KEY, AppHostEnvironment.EMPTY)

        val (processHandler, cliProcessPid) = startProcess(aspireCli, appHostFile, options, envs, eelApi)
        val console = createConsole().apply {
            attachToProcess(processHandler)
        }

        if (appHostDebugger != null) {
            attachDebuggerToAppHost(appHostDebugger, appHostFile, cliProcessPid, processHandler)
        } else if (debugServices) {
            notifyAppHostNotDebugged(appHostFile)
        }

        return DefaultExecutionResult(console, processHandler)
    }

    private suspend fun buildBaseEnvironment(
        options: AspireCliRunConfigurationOptions,
        eelApi: EelApi
    ): Map<String, String> = buildMap {
        if (options.passSystemEnvironment) {
            val systemEnvironment = try {
                eelApi.exec.environmentVariables().eelIt().await()
            } catch (_: EelExecApi.EnvironmentVariablesException) {
                emptyMap()
            }
            putAll(systemEnvironment)
        }
        putAll(options.environmentVariables)
    }

    private suspend fun putAdditionalEnvironmentVariables(envs: MutableMap<String, String>, appHostFile: Path) {
        val aspireWorker = AspireWorker.getInstance(environment.project)
        val (appHost, endpoint) = aspireWorker.startAppHostSessionServer(appHostFile)
        envs[DCP_INSTANCE_ID_PREFIX] = appHost.id.value
        envs.putAll(endpoint.toDcpEnvironmentVariables())

        val aspireEnvironmentVariables = getAspireSpecificEnvironmentVariables(
            envs,
            configuration.cliOptions.usePodmanRuntime
        )
        envs.putAll(aspireEnvironmentVariables)
    }

    private fun configureDebuggingEnvironmentVariables(
        debugServices: Boolean,
        debugAppHost: Boolean,
        envs: MutableMap<String, String>
    ) {
        // Tells the AppHost which mode to put on the launch configurations of its DCP `run_session`
        // requests, which `AspireSessionServer.createSession` maps back into a debug session. Set
        // explicitly in both cases instead of relying on the AppHost's `Debugger.IsAttached`
        // fallback: `aspire run` launches the AppHost as a plain child process, and that fallback
        // does not exist at all for non-"project" launch configuration types.
        envs[DEBUG_SESSION_RUN_MODE] =
            if (debugServices) DEBUG_SESSION_RUN_MODE_DEBUG else DEBUG_SESSION_RUN_MODE_NO_DEBUG

        // Make the AppHost process itself debuggable: `aspire run` launches it as a plain child process, so we
        // cannot debug it through the DCP child-session path. Setting `ASPIRE_WAIT_FOR_DEBUGGER` blocks the AppHost
        // at the first line of `DistributedApplication.CreateBuilder` and spins until a debugger attaches; the
        // applicable `AppHostDebuggerExtension` then attaches. Without such an extension nothing would attach, and
        // the AppHost would spin for the whole `ASPIRE_DEBUGGER_TIMEOUT` before continuing undebugged, so the
        // variable is set only when the AppHost will actually be debugged.
        if (debugAppHost) {
            envs[ASPIRE_WAIT_FOR_DEBUGGER] = "true"
            // The default wait is 30s; give attach (which may follow a build) a comfortable window.
            envs.putIfAbsent(ASPIRE_DEBUGGER_TIMEOUT, "120")
        }
    }

    /**
     * Resolves the extension that attaches a debugger to the AppHost process, or `null` when the AppHost cannot
     * be debugged and only the services it starts are.
     */
    private fun resolveAppHostDebugger(appHostFilePath: Path): AppHostDebuggerExtension? {
        // Attaching to the AppHost process is local-only (the debugger discovers and attaches to a local
        // OS process), so skip it for remote runs (WSL/Docker) rather than risk matching an unrelated
        // local PID.
        if (environment.project.getEelDescriptor() != LocalEelDescriptor) {
            LOG.info("Aspire CLI run is not local; the AppHost process will not be debugged")
            return null
        }

        val debuggerExtension = AppHostDebuggerExtension.findApplicable(appHostFilePath)
        if (debuggerExtension == null) {
            LOG.info("No AppHost debugger extension applies to $appHostFilePath; the AppHost process will not be debugged")
            return null
        }

        return debuggerExtension
    }

    private fun attachDebuggerToAppHost(
        debuggerExtension: AppHostDebuggerExtension,
        appHostFilePath: Path,
        cliProcessPid: Long,
        processHandler: ProcessHandler
    ) {
        val project = environment.project

        // Cancelled on process termination, so a stop mid-attach cancels the polling cleanly.
        val attachLifetime = AspireService.getInstance(project).lifetime.createNested()
        processHandler.addProcessListener(object : ProcessListener {
            override fun processTerminated(event: ProcessEvent) {
                attachLifetime.terminate(true)
            }
        })
        attachLifetime.coroutineScope.launch {
            debuggerExtension.attachToAppHost(appHostFilePath, cliProcessPid, project, attachLifetime)
        }
    }

    private suspend fun checkAndNotifyDevCertificate(environmentVariables: Map<String, String>) {
        if (!environmentVariables.getAspireAllowUnsecuredTransport()) {
            DevCertificateProvider
                .getInstance()
                ?.checkDevCertificate(false, environment.project, showNotification = true)
        }
    }

    private suspend fun startProcess(
        aspireCli: Path,
        appHostFile: Path,
        options: AspireCliRunConfigurationOptions,
        envs: MutableMap<String, String>,
        eelApi: EelApi,
    ): Pair<ProcessHandler, Long> {
        val arguments = buildRunArguments(appHostFile, options.noBuild, options.isolated, options.logLevel)
        val workingDirectory = options.workingDirectory?.takeIf { it.isNotBlank() }?.let { Path.of(it) }
            ?: appHostFile.parent

        return try {
            val parameterList = ParametersListUtil.join(arguments)
            LOG.trace { "Launching aspire CLI ${aspireCli.absolutePathString()} with args: $parameterList" }

            val process = eelApi.exec.spawnProcess(aspireCli.asEelPath())
                .args(arguments)
                .env(envs)
                .applyIf(workingDirectory != null) { workingDirectory(workingDirectory.asEelPath()) }
                .eelIt()
            val pid = process.pid.value
            val commandLineRepresentation = "aspire $parameterList"
            val processHandler =
                object : KillableColoredProcessHandler(process.convertToJVMProcess(), commandLineRepresentation) {
                    override fun readerOptions(): BaseOutputReader.Options =
                        BaseOutputReader.Options.forMostlySilentProcess()
                }
            processHandler.setShouldKillProcessSoftly(true)
            ProcessTerminatedListener.attach(processHandler, environment.project)
            StoppedContainerRuntimeProcessListener.attach(
                processHandler,
                containerRuntimeNotificationCount,
                environment.project
            )
            processHandler to pid
        } catch (e: Exception) {
            rethrowControlFlowException(e)
            LOG.warn("Failed to execute aspire commandline", e)
            throw ExecutionException(
                AspireCoreBundle.message("run.configuration.cli.error.launch.failed", e.message ?: ""), e
            )
        }
    }

    private fun createConsole(): ConsoleView {
        val searchScope = ExecutionSearchScopes.executionScope(environment.project, environment.runProfile)
        val builder = TextConsoleBuilderFactory.getInstance().createBuilder(environment.project, searchScope)
        return builder.console
    }

    private fun buildRunArguments(
        appHostFile: Path,
        noBuild: Boolean = false,
        isolated: Boolean = false,
        logLevel: AspireCliLogLevel? = null,
    ): List<String> = buildList {
        add("run")
        add("--nologo")
        add("--non-interactive")
        add("--apphost")
        add(appHostFile.absolutePathString())
        if (noBuild) add("--no-build")
        if (isolated) add("--isolated")
        logLevel?.let {
            add("--log-level")
            add(it.name)
        }
    }

    private fun notifyCliNotInstalled() {
        Notification(
            NOTIFICATION_GROUP,
            AspireCoreBundle.message("notification.aspire.cli.not.installed.title"),
            AspireCoreBundle.message("notification.aspire.cli.not.installed.content"),
            NotificationType.WARNING
        )
            .addAction(object :
                NotificationAction(AspireCoreBundle.message("notification.aspire.cli.install.action")) {
                override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                    BrowserUtil.browse(INSTALL_URL)
                }
            })
            .notify(environment.project)
    }

    private fun notifyAppHostNotDebugged(appHostFile: Path) {
        val settings = AspireSettings.getInstance()
        if (!settings.showAppHostNotDebuggedNotification) return

        Notification(
            NOTIFICATION_GROUP,
            AspireCoreBundle.message("notification.app.host.not.debugged.title"),
            AspireCoreBundle.message("notification.app.host.not.debugged.content", appHostFile.name),
            NotificationType.INFORMATION
        )
            .addAction(NotificationAction.createSimpleExpiring(
                AspireCoreBundle.message("notification.action.never.show.again")
            ) {
                settings.showAppHostNotDebuggedNotification = false
            })
            .notify(environment.project)
    }
}
