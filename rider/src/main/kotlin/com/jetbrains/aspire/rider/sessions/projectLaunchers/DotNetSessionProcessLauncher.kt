@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.rider.sessions.projectLaunchers

import com.intellij.execution.RunManager
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.ide.browsers.StartBrowserSettings
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.aspire.generated.CreateSessionRequest
import com.jetbrains.aspire.otlp.OpenTelemetryProtocolServerExtension
import com.jetbrains.aspire.run.host.AspireHostConfiguration
import com.jetbrains.aspire.run.AspireConfigurationType
import com.jetbrains.aspire.sessions.DotNetSessionLaunchConfiguration
import com.jetbrains.aspire.rider.sessions.DotNetSessionProcessLauncherExtension
import com.jetbrains.rider.run.configurations.RunnableProjectKinds
import com.jetbrains.rider.runtime.DotNetExecutable
import com.jetbrains.rider.runtime.DotNetRuntime
import com.jetbrains.rider.runtime.RiderDotNetActiveRuntimeHost
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.Path

/**
 * An implementation of the [DotNetSessionProcessLauncherExtension] interface that creates a [DotNetExecutable]
 * from the [CreateSessionRequest] and uses it to create Run/Debug profiles.
 */
abstract class DotNetSessionProcessLauncher : DotNetSessionProcessLauncherExtension {
    companion object {
        private val LOG = logger<DotNetSessionProcessLauncher>()

        private const val OTEL_EXPORTER_OTLP_ENDPOINT = "OTEL_EXPORTER_OTLP_ENDPOINT"
    }

    override suspend fun launchRunProcess(
        sessionId: String,
        launchConfiguration: DotNetSessionLaunchConfiguration,
        sessionProcessEventListener: ProcessListener,
        sessionProcessLifetime: Lifetime,
        aspireHostRunConfigName: String?,
        project: Project
    ) {
        LOG.trace { "Starting run session for ${launchConfiguration.projectPath}" }

        val aspireHostRunConfig = getAspireHostRunConfiguration(aspireHostRunConfigName, project)
        val (executable, _) = getDotNetExecutable(launchConfiguration, false, aspireHostRunConfig, project)
            ?: return
        val executableWithOTLPEndpoint = modifyDotNetExecutableToUseCustomOTLPEndpoint(executable)
        val (modifiedExecutable, callback) = modifyDotNetExecutable(
            executableWithOTLPEndpoint,
            launchConfiguration.projectPath,
            launchConfiguration.launchProfile,
            sessionProcessLifetime,
            project
        )
        val runtime = getDotNetRuntime(modifiedExecutable, project) ?: return

        val projectPath = launchConfiguration.projectPath
        val aspireHostProjectPath = aspireHostRunConfig?.let { Path(it.parameters.projectFilePath) }
        val profile = getRunProfile(
            sessionId,
            projectPath,
            modifiedExecutable,
            runtime,
            sessionProcessEventListener,
            sessionProcessLifetime,
            aspireHostProjectPath
        )

        executeProfile(profile, false, callback, project)
    }

    override suspend fun launchDebugProcess(
        sessionId: String,
        launchConfiguration: DotNetSessionLaunchConfiguration,
        sessionProcessEventListener: ProcessListener,
        sessionProcessLifetime: Lifetime,
        aspireHostRunConfigName: String?,
        project: Project
    ) {
        LOG.trace { "Starting debug session for project ${launchConfiguration.projectPath}" }

        val aspireHostRunConfig = getAspireHostRunConfiguration(aspireHostRunConfigName, project)
        val (executable, browserSettings) = getDotNetExecutable(launchConfiguration, true, aspireHostRunConfig, project)
            ?: return
        val modifiedExecutable = modifyDotNetExecutableToUseCustomOTLPEndpoint(executable)
        val runtime = getDotNetRuntime(modifiedExecutable, project) ?: return

        val projectPath = launchConfiguration.projectPath
        val aspireHostProjectPath = aspireHostRunConfig?.let { Path(it.parameters.projectFilePath) }
        val profile = getDebugProfile(
            sessionId,
            projectPath,
            modifiedExecutable,
            runtime,
            browserSettings,
            sessionProcessEventListener,
            sessionProcessLifetime,
            aspireHostProjectPath
        )

        executeProfile(profile, true, null, project)
    }

    private fun getAspireHostRunConfiguration(name: String?, project: Project): AspireHostConfiguration? {
        if (name == null) return null

        val configurationType = ConfigurationTypeUtil.findConfigurationType(AspireConfigurationType::class.java)
        val runConfiguration = RunManager.getInstance(project)
            .getConfigurationsList(configurationType)
            .singleOrNull { it is AspireHostConfiguration && it.name == name }
        if (runConfiguration == null) {
            LOG.warn("Unable to find Aspire run configuration type: $name")
        }

        return runConfiguration as? AspireHostConfiguration
    }

    private fun getDotNetRuntime(executable: DotNetExecutable, project: Project): DotNetCoreRuntime? {
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

    protected abstract suspend fun getDotNetExecutable(
        launchConfiguration: DotNetSessionLaunchConfiguration,
        isDebugSession: Boolean,
        hostRunConfiguration: AspireHostConfiguration?,
        project: Project
    ): Pair<DotNetExecutable, StartBrowserSettings?>?

    private fun modifyDotNetExecutableToUseCustomOTLPEndpoint(executable: DotNetExecutable): DotNetExecutable {
        val extension = OpenTelemetryProtocolServerExtension.getEnabledExtension()
        val otlpEndpoint = extension?.getOTLPServerEndpoint() ?: return executable

        LOG.trace { "Setting OTEL_EXPORTER_OTLP_ENDPOINT variable to $otlpEndpoint" }
        val envs = executable.environmentVariables.toMutableMap()
        envs[OTEL_EXPORTER_OTLP_ENDPOINT] = otlpEndpoint

        return executable.copy(environmentVariables = envs)
    }

    protected open suspend fun modifyDotNetExecutable(
        executable: DotNetExecutable,
        sessionProjectPath: Path,
        launchProfile: String?,
        lifetime: Lifetime,
        project: Project
    ): Pair<DotNetExecutable, ProgramRunner.Callback?> {
        return executable to null
    }

    protected abstract fun getRunProfile(
        sessionId: String,
        projectPath: Path,
        dotnetExecutable: DotNetExecutable,
        dotnetRuntime: DotNetCoreRuntime,
        sessionProcessEventListener: ProcessListener,
        sessionProcessLifetime: Lifetime,
        aspireHostProjectPath: Path?
    ): RunProfile

    protected abstract fun getDebugProfile(
        sessionId: String,
        projectPath: Path,
        dotnetExecutable: DotNetExecutable,
        dotnetRuntime: DotNetCoreRuntime,
        browserSettings: StartBrowserSettings?,
        sessionProcessEventListener: ProcessListener,
        sessionProcessLifetime: Lifetime,
        aspireHostProjectPath: Path?
    ): RunProfile

    private suspend fun executeProfile(
        profile: RunProfile,
        isDebugSession: Boolean,
        callback: ProgramRunner.Callback?,
        project: Project
    ) {
        val environment =
            if (!isDebugSession) {
                ExecutionEnvironmentBuilder
                    .createOrNull(project, DefaultRunExecutor.getRunExecutorInstance(), profile)
                    ?.modifyExecutionEnvironmentForRun()
                    ?.build()
            } else {
                ExecutionEnvironmentBuilder
                    .createOrNull(project, DefaultDebugExecutor.getDebugExecutorInstance(), profile)
                    ?.modifyExecutionEnvironmentForDebug()
                    ?.build()
            }
        if (environment == null) {
            LOG.warn("Unable to create debug execution environment")
            return
        }

        environment.assignNewExecutionId()
        environment.setProgramCallbacks(callback)

        withContext(Dispatchers.EDT) {
            environment.runner.execute(environment)
        }
    }

    protected open fun ExecutionEnvironmentBuilder.modifyExecutionEnvironmentForRun(): ExecutionEnvironmentBuilder {
        val defaultRunner = ProgramRunner.findRunnerById(DotNetSessionProgramRunner.ID)
        return if (defaultRunner != null) {
            this.runner(defaultRunner)
        } else {
            this
        }
    }

    protected open fun ExecutionEnvironmentBuilder.modifyExecutionEnvironmentForDebug(): ExecutionEnvironmentBuilder {
        val defaultRunner = ProgramRunner.findRunnerById(DotNetSessionDebugProgramRunner.ID)
        return if (defaultRunner != null) {
            this.runner(defaultRunner)
        } else {
            this
        }
    }

    private fun ExecutionEnvironment.setProgramCallbacks(programRunnerCallback: ProgramRunner.Callback? = null) {
        callback = object : ProgramRunner.Callback {
            override fun processStarted(runContentDescriptor: RunContentDescriptor?) {
                runContentDescriptor?.apply {
                    isActivateToolWindowWhenAdded = false
                    isAutoFocusContent = false
                }

                programRunnerCallback?.processStarted(runContentDescriptor)
            }

            override fun processNotStarted(error: Throwable?) {
                programRunnerCallback?.processNotStarted(error)
            }
        }
    }
}