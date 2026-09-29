@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.run.cli

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.ui.ConsoleView
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.platform.eel.*
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.psi.search.ExecutionSearchScopes
import com.intellij.util.applyIf
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.io.BaseOutputReader
import com.jetbrains.aspire.AspireCoreBundle
import com.jetbrains.aspire.extensions.DevCertificateProvider
import com.jetbrains.aspire.run.AspireEnvironment
import com.jetbrains.aspire.run.AsyncRunProfileState
import com.jetbrains.aspire.run.StoppedContainerRuntimeProcessListener
import com.jetbrains.aspire.worker.AppHostListener
import com.jetbrains.aspire.worker.AspireWorker
import com.jetbrains.aspire.worker.dcp.AspireDcpTls
import com.jetbrains.aspire.worker.dcp.AspireEmbeddedSessionHost
import com.jetbrains.aspire.worker.dcp.toDcpEnvironmentVariables
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.absolutePathString

internal class AspireCliRunProfileState(
    private val configuration: AspireCliRunConfiguration,
    private val environment: ExecutionEnvironment
) : AsyncRunProfileState {
    companion object {
        private val LOG = logger<AspireCliRunProfileState>()
    }

    private val containerRuntimeNotificationCount = AtomicInteger()

    override suspend fun executeSuspending(executor: Executor, programRunner: ProgramRunner<*>): ExecutionResult {
        val options = configuration.cliOptions
        val project = environment.project

        val appHostFilePath = options.appHostFilePath?.takeIf { it.isNotBlank() }?.let { Path.of(it) }
        if (appHostFilePath == null) {
            throw ExecutionException(AspireCoreBundle.message("run.configuration.cli.error.no.app.host"))
        }

        val aspireCliPath = AspireCliLocator.getInstance(project).locate()?.asNioPath()
        if (aspireCliPath == null) {
            AspireCliNotifications.notifyCliNotInstalled(project)
            throw ExecutionException(AspireCoreBundle.message("run.configuration.cli.error.cli.not.found"))
        }

        val eelApi = project.getEelDescriptor().toEelApi()

        val envs = buildBaseEnvironment(options, eelApi).toMutableMap()
        val aspireEnvironment = configureEnvironmentVariables(appHostFilePath, envs)

        checkAndNotifyDevCertificate(aspireEnvironment)

        project.messageBus
            .syncPublisher(AppHostListener.TOPIC)
            .appHostStarting(appHostFilePath, aspireEnvironment.appHostEnvironment)

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

    private suspend fun configureEnvironmentVariables(
        appHostFilePath: Path,
        envs: MutableMap<String, String>,
    ): AspireEnvironment.Result {
        val aspireWorker = AspireWorker.getInstance(environment.project)
        val appHost = if (AspireEmbeddedSessionHost.isEnabled()) {
            //Embedded mode: each AppHost runs its own in-process DCP server; no external worker process.
            val appHost = requireNotNull(aspireWorker.getOrCreateAppHostByPath(appHostFilePath))
            val tlsMaterial = AspireDcpTls.getInstance(environment.project).getOrComputeTlsMaterial()
            val endpoint = appHost.startSessionServer(tlsMaterial?.tls)
            envs.putAll(endpoint.toDcpEnvironmentVariables(tlsMaterial?.base64Cert))
            appHost
        } else {
            aspireWorker.start()
            envs.putAll(aspireWorker.getEnvironmentVariablesForDcpConnection())
            requireNotNull(aspireWorker.getOrCreateAppHostByPath(appHostFilePath))
        }

        val result = AspireEnvironment.configure(
            appHost = appHost,
            browserUrl = null,
            usePodmanRuntime = configuration.cliOptions.usePodmanRuntime,
            envs = envs
        )
        return result
    }

    private suspend fun checkAndNotifyDevCertificate(aspireEnvironment: AspireEnvironment.Result) {
        if (!aspireEnvironment.useHttp) {
            DevCertificateProvider.getInstance()?.checkDevCertificate(true, environment.project)
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

    private fun maybeOpenBrowser(startBrowser: Boolean, url: String?, processHandler: AspireCliProcessHandler) {
        if (!startBrowser || url.isNullOrBlank()) return

        processHandler.addProcessListener(object : ProcessListener {
            override fun startNotified(event: ProcessEvent) {
                BrowserUtil.browse(url)
            }
        })
    }
}
