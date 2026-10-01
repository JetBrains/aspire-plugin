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
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.options.ex.ConfigurableVisitor
import com.intellij.platform.eel.*
import com.intellij.platform.eel.provider.*
import com.intellij.platform.util.coroutines.childScope
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
import com.jetbrains.aspire.run.AspireExecutionListener.Companion.APP_HOST_ID_KEY
import com.jetbrains.aspire.run.AsyncRunProfileState
import com.jetbrains.aspire.run.StoppedContainerRuntimeProcessListener
import com.jetbrains.aspire.settings.AspireSettings
import com.jetbrains.aspire.util.*
import com.jetbrains.aspire.worker.AspireAppHost.AppHostEnvironment
import com.jetbrains.aspire.worker.AspireWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Predicate
import kotlin.io.path.absolutePathString
import kotlin.io.path.name
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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
        val appHostAttachTimeout = AspireSettings.getInstance().appHostAttachTimeoutSeconds.seconds
        configureDebuggingEnvironmentVariables(debugServices, appHostDebugger != null, appHostAttachTimeout, envs)

        checkAndNotifyDevCertificate(envs)

        environment.putUserData(APP_HOST_ENVIRONMENT_KEY, AppHostEnvironment.EMPTY)

        val (processHandler, cliProcessPid) = startProcess(aspireCli, appHostFile, options, envs, eelApi)
        val console = createConsole().apply {
            attachToProcess(processHandler)
        }

        if (appHostDebugger != null) {
            attachDebuggerToAppHost(appHostDebugger, appHostFile, cliProcessPid, appHostAttachTimeout, processHandler)
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
        environment.putUserData(APP_HOST_ID_KEY, appHost.id)
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
        appHostAttachTimeout: Duration,
        envs: MutableMap<String, String>
    ) {
        envs[DEBUG_SESSION_RUN_MODE] = if (debugServices) DEBUG_SESSION_RUN_MODE_DEBUG else DEBUG_SESSION_RUN_MODE_NO_DEBUG
        if (debugAppHost) {
            envs[ASPIRE_WAIT_FOR_DEBUGGER] = "true"
            envs[ASPIRE_DEBUGGER_TIMEOUT] = appHostAttachTimeout.inWholeSeconds.toString()
        }
    }

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
        attachTimeout: Duration,
        processHandler: ProcessHandler
    ) {
        val project = environment.project

        // Canceled on process termination, so a stop mid-attach cancels the polling cleanly.
        val attachScope = AspireService.getInstance(project).scope.childScope("Aspire AppHost debugger attach")
        processHandler.addProcessListener(object : ProcessListener {
            override fun processTerminated(event: ProcessEvent) {
                attachScope.cancel()
            }
        })
        attachScope.launch {
            val attached = try {
                withTimeoutOrNull(attachTimeout) {
                    debuggerExtension.attachToAppHost(appHostFilePath, cliProcessPid, project, attachScope).getOrLogException(LOG)
                    true
                } ?: run {
                    LOG.warn("The debugger was not attached to the AppHost $appHostFilePath within $attachTimeout")
                    false
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LOG.warn("Failed to attach the debugger to the AppHost $appHostFilePath", e)
                false
            }
            // Not reached when the run is stopped mid-attach: cancelling the scope cancels the coroutine.
            if (!attached) {
                notifyAppHostAttachFailed(appHostFilePath)
                // Nothing is attached, so nothing bound to the scope is needed any longer.
                attachScope.cancel()
            }
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

    private fun notifyAppHostAttachFailed(appHostFile: Path) {
        val project = environment.project
        Notification(
            NOTIFICATION_GROUP,
            AspireCoreBundle.message("notification.app.host.attach.failed.title"),
            AspireCoreBundle.message("notification.app.host.attach.failed.content", appHostFile.name),
            NotificationType.WARNING
        )
            .addAction(NotificationAction.createSimpleExpiring(
                AspireCoreBundle.message("notification.action.aspire.settings")
            ) {
                ShowSettingsUtil.getInstance().showSettingsDialog(
                    project,
                    Predicate { ConfigurableVisitor.getId(it) == AspireSettings.CONFIGURABLE_ID },
                    null
                )
            })
            .notify(project)
    }
}
