@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.run.cli

import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputType
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.eel.spawnProcess
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.EnvironmentUtil
import com.intellij.util.execution.ParametersListUtil
import com.jetbrains.aspire.AspireCoreBundle
import com.jetbrains.aspire.AspireService
import com.jetbrains.aspire.worker.AppHostListener
import com.jetbrains.aspire.worker.AppHostLogEntry
import com.jetbrains.aspire.worker.AspireWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
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
) : RunProfileState {
    companion object {
        private val LOG = logger<AspireCliRunProfileState>()
    }

    override fun execute(executor: Executor, runner: ProgramRunner<*>): ExecutionResult =
        runBlockingCancellable { executeSuspend(executor) }

    private suspend fun executeSuspend(executor: Executor): ExecutionResult {
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
        val environmentResult = AspireCliEnvironment.configure(appHost, options.browserUrl, envs)

        // Only the HTTPS transport needs a trusted dev certificate.
        if (!environmentResult.useHttp) {
            trustDevCertificatesWithAspireCli(project, aspireCliPath)
        }

        project.messageBus
            .syncPublisher(AppHostListener.TOPIC)
            .appHostStarting(appHostFilePath, environmentResult.appHostEnvironment)

        // Debug enables DCP IDE-execution: start the (Rider) AspireWorker and merge its DCP connection env
        // so `aspire run` delegates child sessions to the IDE. Without debug, `aspire run` orchestrates
        // children itself and no worker is needed.
        // TODO: replace the external AspireWorker with the embedded DCP server (AspireSessionServer).
        val debug = executor.id == DefaultDebugExecutor.EXECUTOR_ID || options.enableIdeDebugging
        if (debug) {
            val aspireWorker = AspireWorker.getInstance(project)
            aspireWorker.start()
            envs.putAll(aspireWorker.getEnvironmentVariablesForDcpConnection())
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

        wireAppHostLifecycle(project, appHostFilePath, configuration.name, processHandler, processScope)

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
        add("--project")
        add(appHostFilePath)
        userArguments?.takeIf { it.isNotBlank() }?.let { addAll(ParametersListUtil.parse(it)) }
    }

    private fun wireAppHostLifecycle(
        project: Project,
        appHostFilePath: Path,
        runConfigName: String,
        processHandler: AspireCliProcessHandler,
        processScope: CoroutineScope
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
