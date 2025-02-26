@file:Suppress("UnstableApiUsage")

package com.jetbrains.rider.aspire.sessionHost.projectLaunchers

import com.intellij.execution.Executor
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
import com.jetbrains.rider.aspire.generated.CreateSessionRequest
import com.jetbrains.rider.aspire.run.AspireHostConfiguration
import com.jetbrains.rider.aspire.run.AspireHostConfigurationType
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
 * An implementation of the `SessionProcessLauncherExtension` interface that creates a [DotNetExecutable]
 * from the [CreateSessionRequest] and uses it to create Run/Debug profiles.
 */
abstract class DotNetExecutableSessionProcessLauncher : SessionProcessLauncherExtension {
    companion object {
        private val LOG = logger<DotNetExecutableSessionProcessLauncher>()
    }

    override suspend fun launchRunProcess(
        sessionId: String,
        sessionModel: CreateSessionRequest,
        sessionProcessEventListener: ProcessListener,
        sessionProcessLifetime: Lifetime,
        aspireHostRunConfigName: String?,
        project: Project
    ) {
        LOG.trace { "Starting run session for ${sessionModel.projectPath}" }

        val aspireHostRunConfig = getAspireHostRunConfiguration(aspireHostRunConfigName, project)
        val (executable, _) = getDotNetExecutable(sessionModel, false, aspireHostRunConfig, project)
            ?: return
        val (modifiedExecutable, callback) = modifyDotNetExecutable(
            executable,
            Path(sessionModel.projectPath),
            sessionModel.launchProfile,
            sessionProcessLifetime,
            project
        )
        val runtime = getDotNetRuntime(modifiedExecutable, project) ?: return

        val projectPath = Path(sessionModel.projectPath)
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

        executeProfile(profile, DefaultRunExecutor.getRunExecutorInstance(), callback, project)
    }

    override suspend fun launchDebugProcess(
        sessionId: String,
        sessionModel: CreateSessionRequest,
        sessionProcessEventListener: ProcessListener,
        sessionProcessLifetime: Lifetime,
        aspireHostRunConfigName: String?,
        project: Project
    ) {
        LOG.trace { "Starting debug session for project ${sessionModel.projectPath}" }

        val aspireHostRunConfig = getAspireHostRunConfiguration(aspireHostRunConfigName, project)
        val (executable, browserSettings) = getDotNetExecutable(sessionModel, true, aspireHostRunConfig, project)
            ?: return
        val runtime = getDotNetRuntime(executable, project) ?: return

        val projectPath = Path(sessionModel.projectPath)
        val aspireHostProjectPath = aspireHostRunConfig?.let { Path(it.parameters.projectFilePath) }
        val profile = getDebugProfile(
            sessionId,
            projectPath,
            executable,
            runtime,
            browserSettings,
            sessionProcessEventListener,
            sessionProcessLifetime,
            aspireHostProjectPath
        )

        executeProfile(profile, DefaultDebugExecutor.getDebugExecutorInstance(), null, project)
    }

    private fun getAspireHostRunConfiguration(name: String?, project: Project): AspireHostConfiguration? {
        if (name == null) return null

        val configurationType = ConfigurationTypeUtil.findConfigurationType(AspireHostConfigurationType::class.java)
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
        sessionModel: CreateSessionRequest,
        isDebugSession: Boolean,
        hostRunConfiguration: AspireHostConfiguration?,
        project: Project
    ): Pair<DotNetExecutable, StartBrowserSettings?>?

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
        executor: Executor,
        callback: ProgramRunner.Callback?,
        project: Project
    ) {
        val environment = ExecutionEnvironmentBuilder
            .createOrNull(project, executor, profile)
            ?.modifyExecutionEnvironment()
            ?.build()
        if (environment == null) {
            LOG.warn("Unable to create debug execution environment")
            return
        }

        environment.setProgramCallbacks(callback)

        withContext(Dispatchers.EDT) {
            environment.runner.execute(environment)
        }
    }

    protected open fun ExecutionEnvironmentBuilder.modifyExecutionEnvironment(): ExecutionEnvironmentBuilder {
        return this
    }

    private fun ExecutionEnvironment.setProgramCallbacks(hotReloadCallback: ProgramRunner.Callback? = null) {
        callback = object : ProgramRunner.Callback {
            override fun processStarted(runContentDescriptor: RunContentDescriptor?) {
                runContentDescriptor?.apply {
                    isActivateToolWindowWhenAdded = false
                    isAutoFocusContent = false
                }

                hotReloadCallback?.processStarted(runContentDescriptor)
            }

            override fun processNotStarted(error: Throwable?) {
                hotReloadCallback?.processNotStarted(error)
            }
        }
    }
}