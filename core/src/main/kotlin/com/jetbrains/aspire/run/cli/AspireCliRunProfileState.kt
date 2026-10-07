@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.run.cli

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
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
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.psi.search.ExecutionSearchScopes
import com.intellij.util.applyIf
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.io.BaseOutputReader
import com.jetbrains.aspire.AspireCoreBundle
import com.jetbrains.aspire.cli.AspireCliLocator
import com.jetbrains.aspire.cli.AspireCliLogLevel
import com.jetbrains.aspire.extensions.DevCertificateProvider
import com.jetbrains.aspire.util.getAspireSpecificEnvironmentVariables
import com.jetbrains.aspire.run.AsyncRunProfileState
import com.jetbrains.aspire.run.StoppedContainerRuntimeProcessListener
import com.jetbrains.aspire.util.DCP_INSTANCE_ID_PREFIX
import com.jetbrains.aspire.util.getAspireAllowUnsecuredTransport
import com.jetbrains.aspire.util.getAspireDashboardOtlpEndpointUrl
import com.jetbrains.aspire.util.getAspireDashboardResourceServiceApiKey
import com.jetbrains.aspire.util.getAspireResourceServiceEndpointUrl
import com.jetbrains.aspire.worker.AppHostListener
import com.jetbrains.aspire.worker.AspireAppHost.AppHostEnvironment
import com.jetbrains.aspire.worker.AspireWorker
import com.jetbrains.aspire.dcp.toDcpEnvironmentVariables
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.absolutePathString

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

        checkAndNotifyDevCertificate(envs)

        val resourceServiceEndpointUrl = envs.getAspireResourceServiceEndpointUrl()
        val resourceServiceApiKey = envs.getAspireDashboardResourceServiceApiKey()
        val otlpEndpointUrl = envs.getAspireDashboardOtlpEndpointUrl()
        val appHostEnvironment = AppHostEnvironment(
            resourceServiceEndpointUrl,
            resourceServiceApiKey,
            otlpEndpointUrl
        )
        project.messageBus
            .syncPublisher(AppHostListener.TOPIC)
            .appHostStarting(appHostFile, appHostEnvironment)

        val processHandler = startProcess(aspireCli, appHostFile, options, envs, eelApi)
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
    ): ProcessHandler {
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
}
