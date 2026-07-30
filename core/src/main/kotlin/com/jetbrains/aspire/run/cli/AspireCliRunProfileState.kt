@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.run.cli

import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputType
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.eel.spawnProcess
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.EnvironmentUtil
import com.intellij.util.execution.ParametersListUtil
import com.jetbrains.aspire.AspireCoreBundle
import com.jetbrains.aspire.AspireService
import com.jetbrains.aspire.extensions.AppHostDebuggerExtension
import com.jetbrains.aspire.generated.AspireHostModelConfig
import com.jetbrains.aspire.util.*
import com.jetbrains.aspire.worker.AppHostListener
import com.jetbrains.aspire.worker.AppHostLogEntry
import com.jetbrains.aspire.worker.AspireWorker
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

/**
 * Runs `aspire run` for an [AspireCliRunConfiguration] via eel and wires it into the Services tool window.
 *
 * Flow: resolve the CLI → trust dev certs (HTTPS only) → obtain/create the [com.jetbrains.aspire.worker.AspireAppHost]
 * → compute the environment → publish `appHostStarting` → (Debug/IDE-debugging) start the embedded DCP server and
 * merge its env → eel-launch the process → publish `appHostStarted`/`appHostStopped` around a console + log flow.
 */
internal class AspireCliRunProfileState(
    private val configuration: AspireCliRunConfiguration,
    private val environment: ExecutionEnvironment
) : AsyncRunProfileState {
    companion object {
        private val LOG = logger<AspireCliRunProfileState>()
    }

    override fun execute(executor: Executor, programRunner: ProgramRunner<*>): ExecutionResult =
        runBlockingCancellable { executeSuspending(executor, programRunner) }

    override suspend fun executeSuspending(executor: Executor, programRunner: ProgramRunner<*>): ExecutionResult {
        val project = environment.project
        val options = configuration.cliOptions

        val appHostFilePathString = options.appHostFilePath
        if (appHostFilePathString.isNullOrBlank()) {
            throw ExecutionException(AspireCoreBundle.message("run.configuration.cli.error.no.app.host"))
        }
        val appHostFilePath = Path(appHostFilePathString)

        val aspireCliPath = AspireCliLocator.locate(project, options.aspireCliPath)
        if (aspireCliPath == null) {
            AspireCliNotifications.notifyCliNotInstalled(project)
            throw ExecutionException(AspireCoreBundle.message("run.configuration.cli.error.cli.not.found"))
        }

        val appHost = AspireWorker.getInstance(project).getOrCreateAppHostByPath(appHostFilePath)
            ?: throw ExecutionException(AspireCoreBundle.message("run.configuration.cli.error.app.host.not.found", appHostFilePathString))

        val envs = buildBaseEnvironment(options)

        // IDE execution enables DCP IDE-execution: start the (Rider) AspireWorker and collect its DCP
        // connection env, which `configure` merges into `envs` so `aspire run` delegates child sessions to
        // the IDE. Without it, `aspire run` orchestrates children itself and no worker is needed. Whether
        // those sessions are debugged is a separate question, driven by the executor: `enableIdeDebugging`
        // only opts into IDE execution.
        // TODO: replace the external AspireWorker with the embedded DCP server (AspireSessionServer).
        val attachDebugger = executor.id == DefaultDebugExecutor.EXECUTOR_ID
        val useIdeExecution = attachDebugger || options.enableIdeDebugging
        val dcpEnvironmentVariables = if (useIdeExecution) {
            val aspireWorker = AspireWorker.getInstance(project)
            aspireWorker.start()
            aspireWorker.getEnvironmentVariablesForDcpConnection()
        } else {
            emptyMap()
        }

        // On the Debug executor, make the AppHost process itself debuggable: `aspire run` launches it as a
        // plain child process, so we cannot debug it through the DCP child-session path. Setting
        // ASPIRE_WAIT_FOR_DEBUGGER blocks the AppHost at the first line of `DistributedApplication.CreateBuilder`,
        // where it prints `AppHost PID: <pid>` and spins until a debugger attaches. The applicable
        // AppHostDebuggerExtension (resolved below) reads that PID and attaches. Gated strictly on the Debug
        // executor, not `enableIdeDebugging` (which only opts into DCP IDE-execution for child resources).
        if (attachDebugger) {
            envs[ASPIRE_WAIT_FOR_DEBUGGER] = "true"
            // The default wait is 30s; give attach (which may follow a build) a comfortable window.
            envs.putIfAbsent(ASPIRE_DEBUGGER_TIMEOUT, "120")
        }

        val environmentResult = AspireCliEnvironment.configure(appHost, options.browserUrl, dcpEnvironmentVariables, usePodmanRuntime = false, envs)

        // Only the HTTPS transport needs a trusted dev certificate.
        if (!environmentResult.useHttp) {
            trustDevCertificatesWithAspireCli(project, aspireCliPath)
        }

        project.messageBus
            .syncPublisher(AppHostListener.TOPIC)
            .appHostStarting(appHostFilePath, environmentResult.appHostEnvironment)

        var dcpCleanup: (() -> Unit)? = null
        if (useIdeExecution) {
            val aspireWorker = AspireWorker.getInstance(project)

            // Tells the AppHost which mode to put on the launch configurations of its DCP `run_session`
            // requests, which AspireSessionServer.createSession maps back into a debug session. Set
            // explicitly in both branches instead of relying on the AppHost's `Debugger.IsAttached`
            // fallback: `aspire run` launches the AppHost as a plain child process, and that fallback
            // does not exist at all for non-"project" launch configuration types.
            envs[DEBUG_SESSION_RUN_MODE] =
                if (attachDebugger) DEBUG_SESSION_RUN_MODE_DEBUG else DEBUG_SESSION_RUN_MODE_NO_DEBUG

            // Register the AppHost model with the worker before launching so the DCP IDE-execution
            // handshake succeeds: the worker resolves the host by DCP instance prefix for the `/notify`
            // session-event stream, and the IDE starts draining this host's session events to DCP.
            val hostConfig = AspireHostModelConfig(
                appHost.dcpInstancePrefix,
                appHostFilePath.absolutePathString(),
                environmentResult.appHostEnvironment.resourceServiceEndpointUrl,
                environmentResult.appHostEnvironment.resourceServiceApiKey,
                environmentResult.appHostEnvironment.otlpEndpointUrl,
                environmentResult.appHostEnvironment.aspireHostProjectUrl
            )
            withContext(Dispatchers.EDT) {
                aspireWorker.startAspireHostModel(hostConfig)
            }

            dcpCleanup = {
                ApplicationManager.getApplication().invokeLater {
                    aspireWorker.stopAspireHostModel(hostConfig.id)
                }
            }
        }

        val arguments = buildArguments(appHostFilePathString, options.arguments)
        val workingDirectory = options.workingDirectory?.takeIf { it.isNotBlank() }
            ?: appHostFilePath.parent?.absolutePathString()

        val eelApi = project.getEelDescriptor().toEelApi()
        val eelProcess = try {
            var builder = eelApi.exec.spawnProcess(aspireCliPath)
                .args(arguments)
                .env(envs)
            if (workingDirectory != null) {
                builder = builder.workingDirectory(EelPath.parse(workingDirectory, project.getEelDescriptor()))
            }
            builder.eelIt()
        } catch (e: ExecutionException) {
            throw e
        } catch (e: Exception) {
            throw ExecutionException(AspireCoreBundle.message("run.configuration.cli.error.launch.failed", e.message ?: ""), e)
        }

        val commandLineText = (listOf(aspireCliPath) + arguments).joinToString(" ")
        LOG.trace { "Launched aspire CLI: $commandLineText" }

        val processScope = AspireService.getInstance(project).scope.childScope("Aspire CLI: ${configuration.name}")
        val processHandler = AspireCliProcessHandler(eelProcess, processScope, commandLineText)

        wireAppHostLifecycle(project, appHostFilePath, configuration.name, processHandler, processScope, dcpCleanup)

        if (attachDebugger) {
            // Attaching to the AppHost process is local-only (the debugger discovers and attaches to a local
            // OS process), so skip it for remote runs (WSL/Docker) rather than risk matching an unrelated
            // local PID. `ASPIRE_WAIT_FOR_DEBUGGER` is still set above so child-session debugging behaves the
            // same; only the AppHost-process attach is skipped.
            if (project.getEelDescriptor() != LocalEelDescriptor) {
                LOG.info("Aspire CLI run is not local; the AppHost process will not be debugged")
            } else {
                val debuggerExtension = AppHostDebuggerExtension.findApplicable(appHostFilePath)
                if (debuggerExtension != null) {
                    // `processScope` is cancelled on process termination in `wireAppHostLifecycle`, so a stop
                    // mid-attach cancels the polling cleanly.
                    val cliProcessPid = eelProcess.pid.value
                    processScope.launch { debuggerExtension.attachToAppHost(appHostFilePath, cliProcessPid, project) }
                } else {
                    LOG.info("No AppHost debugger extension applies to $appHostFilePath; the AppHost process will not be debugged")
                }
            }
        }

        maybeOpenBrowser(options.startBrowserAfterLaunch, environmentResult.appHostEnvironment.aspireHostProjectUrl, processHandler)

        val console = TextConsoleBuilderFactory.getInstance().createBuilder(project).console
        console.attachToProcess(processHandler)

        return DefaultExecutionResult(console, processHandler)
    }

    private fun buildBaseEnvironment(options: AspireCliRunConfigurationOptions): MutableMap<String, String> {
        val envs = LinkedHashMap<String, String>()
        if (options.passParentEnvs) {
            envs.putAll(EnvironmentUtil.getEnvironmentMap())
        }
        envs.putAll(options.envs)
        return envs
    }

    private fun buildArguments(appHostFilePath: String, userArguments: String?): List<String> = buildList {
        add("run")
        add("--nologo")
        add("--apphost")
        add(appHostFilePath)
        userArguments?.takeIf { it.isNotBlank() }?.let { addAll(ParametersListUtil.parse(it)) }
    }

    private fun wireAppHostLifecycle(
        project: Project,
        appHostFilePath: Path,
        runConfigName: String,
        processHandler: AspireCliProcessHandler,
        processScope: CoroutineScope,
        dcpCleanup: (() -> Unit)?
    ) {
        val logFlow = MutableSharedFlow<AppHostLogEntry>(
            replay = 100,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        processHandler.addProcessListener(object : ProcessListener {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                logFlow.tryEmit(AppHostLogEntry(event.text, outputType == ProcessOutputType.STDERR))
            }

            override fun processTerminated(event: ProcessEvent) {
                project.messageBus
                    .syncPublisher(AppHostListener.TOPIC)
                    .appHostStopped(appHostFilePath)

                dcpCleanup?.invoke()
                processScope.cancel()
            }
        })

        project.messageBus
            .syncPublisher(AppHostListener.TOPIC)
            .appHostStarted(appHostFilePath, runConfigName, logFlow.asSharedFlow())
    }

    private fun maybeOpenBrowser(startBrowser: Boolean, url: String?, processHandler: AspireCliProcessHandler) {
        if (!startBrowser || url.isNullOrBlank()) return

        processHandler.addProcessListener(object : ProcessListener {
            override fun startNotified(event: ProcessEvent) {
                BrowserUtil.browse(url)
            }
        })
    }
}
