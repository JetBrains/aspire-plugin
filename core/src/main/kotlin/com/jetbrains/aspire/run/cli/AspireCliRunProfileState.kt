@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.run.cli

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputType
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.ui.ConsoleView
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelExecApi
import com.intellij.platform.eel.convertToJVMProcess
import com.intellij.platform.eel.environmentVariables
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.eel.spawnProcess
import com.intellij.platform.util.coroutines.childScope
import com.intellij.psi.search.ExecutionSearchScopes
import com.intellij.util.applyIf
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.io.BaseOutputReader
import com.jetbrains.aspire.AspireCoreBundle
import com.jetbrains.aspire.AspireService
import com.jetbrains.aspire.common.AsyncRunProfileState
import com.jetbrains.aspire.extensions.DevCertificateProvider
import com.jetbrains.aspire.worker.AppHostListener
import com.jetbrains.aspire.worker.AppHostLogEntry
import com.jetbrains.aspire.worker.AspireWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.nio.file.Path
import kotlin.io.path.absolutePathString

internal class AspireCliRunProfileState(
    private val configuration: AspireCliRunConfiguration,
    private val environment: ExecutionEnvironment
) : AsyncRunProfileState {
    companion object {
        private val LOG = logger<AspireCliRunProfileState>()
    }

    override suspend fun executeSuspending(executor: Executor, programRunner: ProgramRunner<*>): ExecutionResult {
        val project = environment.project
        val options = configuration.cliOptions

        val appHostFilePath = options.appHostFilePath?.takeIf { it.isNotBlank() }?.let { Path.of(it) }
        if (appHostFilePath == null) {
            throw ExecutionException(AspireCoreBundle.message("run.configuration.cli.error.no.app.host"))
        }

        val aspireCliPath = AspireCliLocator.getInstance(project).locate()?.asNioPath()
        if (aspireCliPath == null) {
            AspireCliNotifications.notifyCliNotInstalled(project)
            throw ExecutionException(AspireCoreBundle.message("run.configuration.cli.error.cli.not.found"))
        }

        val appHost = AspireWorker.getInstance(project).getOrCreateAppHostByPath(appHostFilePath)
        if (appHost == null) {
            throw ExecutionException(AspireCoreBundle.message("run.configuration.cli.error.unable.to.run"))
        }

        val eelApi = project.getEelDescriptor().toEelApi()

        val envs = buildBaseEnvironment(options, eelApi).toMutableMap()
        val aspireEnvironment =
            AspireCliEnvironment.configure(appHost, options.browserUrl, options.usePodmanRuntime, envs)

        checkAndNotifyDevCertificate(aspireEnvironment, project)

        project.messageBus
            .syncPublisher(AppHostListener.TOPIC)
            .appHostStarting(appHostFilePath, aspireEnvironment.appHostEnvironment)

        val aspireWorker = AspireWorker.getInstance(project)
        aspireWorker.start()
        envs.putAll(aspireWorker.getEnvironmentVariablesForDcpConnection())

        val processHandler = startProcess(aspireCliPath, appHostFilePath, options, envs, eelApi)
        val console = createConsole().apply {
            attachToProcess(processHandler)
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

    private suspend fun checkAndNotifyDevCertificate(aspireEnvironment: AspireCliEnvironment.Result, project: Project) {
        if (!aspireEnvironment.useHttp) {
            DevCertificateProvider.getInstance()?.checkDevCertificate(true, project)
            //TODO: Show a notification
        }
    }

    private suspend fun startProcess(
        aspireCliPath: Path,
        appHostFilePath: Path,
        options: AspireCliRunConfigurationOptions,
        envs: MutableMap<String, String>,
        eelApi: EelApi,
    ): ProcessHandler {
        val arguments = buildRunArguments(appHostFilePath, options.noBuild, options.isolated, options.logLevel)
        val workingDirectory = options.workingDirectory?.takeIf { it.isNotBlank() }?.let { Path.of(it) }
            ?: appHostFilePath.parent

        return try {
            val parameterList = ParametersListUtil.join(arguments)
            LOG.trace { "Launching aspire CLI ${aspireCliPath.absolutePathString()} with args: $parameterList" }

            val process = eelApi.exec.spawnProcess(aspireCliPath.asEelPath())
                .args(arguments)
                .env(envs)
                .applyIf(workingDirectory != null) { workingDirectory(workingDirectory.asEelPath()) }
                .eelIt()
            val commandLineRepresentation = "aspire $parameterList"
            val processHandler = KillableColoredProcessHandler(process.convertToJVMProcess(), commandLineRepresentation)
            processHandler.setShouldKillProcessSoftly(true)
            ProcessTerminatedListener.attach(processHandler, environment.project)
            processHandler
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
        appHostFilePath: Path,
        noBuild: Boolean = false,
        isolated: Boolean = false,
        logLevel: AspireCliLogLevel? = null,
    ): List<String> = buildList {
        add("run")
        add("--nologo")
        add("--non-interactive")
        add("--apphost")
        add(appHostFilePath.absolutePathString())
        if (noBuild) add("--no-build")
        if (isolated) add("--isolated")
        logLevel?.let {
            add("--log-level")
            add(it.name)
        }
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
