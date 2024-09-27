@file:Suppress("UnstableApiUsage")

package com.jetbrains.rider.aspire.sessionHost.projectLaunchers

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.runners.ProgramRunner
import com.intellij.ide.browsers.StartBrowserSettings
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.aspire.generated.SessionModel
import com.jetbrains.rider.aspire.run.AspireHostConfiguration
import com.jetbrains.rider.aspire.sessionHost.SessionExecutableFactory
import com.jetbrains.rider.aspire.sessionHost.findBySessionProject
import com.jetbrains.rider.model.runnableProjectsModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.run.configurations.RunnableProjectKinds
import com.jetbrains.rider.run.configurations.RuntimeHotReloadRunConfigurationInfo
import com.jetbrains.rider.run.configurations.launchSettings.LaunchSettingsJsonService
import com.jetbrains.rider.runtime.DotNetExecutable
import com.jetbrains.rider.runtime.DotNetRuntime
import com.jetbrains.rider.runtime.RiderDotNetActiveRuntimeHost
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.nameWithoutExtension

abstract class BaseProjectSessionProcessLauncher : SessionProcessLauncherExtension {
    companion object {
        private val LOG = logger<BaseProjectSessionProcessLauncher>()
    }

    protected abstract val hotReloadExtension: AspireProjectHotReloadConfigurationExtension

    override suspend fun launchRunProcess(
        sessionId: String,
        sessionModel: SessionModel,
        sessionProcessEventListener: ProcessListener,
        sessionProcessTerminatedListener: ProcessListener,
        sessionProcessLifetime: Lifetime,
        hostRunConfiguration: AspireHostConfiguration?,
        project: Project
    ) {
        LOG.trace { "Starting run session for ${sessionModel.projectPath}" }

        val (executable, _) = getDotNetExecutable(sessionModel, hostRunConfiguration, true, project) ?: return
        val (executableWithHotReload, hotReloadCallback) = enableHotReload(
            executable,
            Path(sessionModel.projectPath),
            sessionModel.launchProfile,
            sessionProcessLifetime,
            project
        )
        val runtime = getDotNetRuntime(executableWithHotReload, project) ?: return

        val projectName = Path(sessionModel.projectPath).nameWithoutExtension
        val profile = getRunProfile(
            sessionId,
            projectName,
            executableWithHotReload,
            runtime,
            sessionProcessEventListener,
            sessionProcessTerminatedListener,
            sessionProcessLifetime
        )

        val environment = ExecutionEnvironmentBuilder
            .createOrNull(project, DefaultRunExecutor.getRunExecutorInstance(), profile)
            ?.build()
        if (environment == null) {
            LOG.warn("Unable to create run execution environment")
            return
        }

        hotReloadCallback?.let { environment.callback = it }

        withContext(Dispatchers.EDT) {
            environment.runner.execute(environment)
        }
    }

    protected abstract fun getRunProfile(
        sessionId: String,
        projectName: String,
        dotnetExecutable: DotNetExecutable,
        dotnetRuntime: DotNetCoreRuntime,
        sessionProcessEventListener: ProcessListener,
        sessionProcessTerminatedListener: ProcessListener,
        sessionProcessLifetime: Lifetime
    ): RunProfile

    override suspend fun launchDebugProcess(
        sessionId: String,
        sessionModel: SessionModel,
        sessionProcessEventListener: ProcessListener,
        sessionProcessTerminatedListener: ProcessListener,
        sessionProcessLifetime: Lifetime,
        hostRunConfiguration: AspireHostConfiguration?,
        project: Project
    ) {
        LOG.trace { "Starting debug session for project ${sessionModel.projectPath}" }

        val (executable, browserSettings) = getDotNetExecutable(sessionModel, hostRunConfiguration, false, project) ?: return
        val runtime = getDotNetRuntime(executable, project) ?: return

        val projectPath = Path(sessionModel.projectPath)
        val profile = getDebugProfile(
            sessionId,
            projectPath.nameWithoutExtension,
            projectPath,
            executable,
            runtime,
            browserSettings,
            sessionProcessEventListener,
            sessionProcessTerminatedListener,
            sessionProcessLifetime
        )

        val environment = ExecutionEnvironmentBuilder
            .createOrNull(project, DefaultDebugExecutor.getDebugExecutorInstance(), profile)
            ?.build()
        if (environment == null) {
            LOG.warn("Unable to create debug execution environment")
            return
        }

        withContext(Dispatchers.EDT) {
            environment.runner.execute(environment)
        }
    }

    protected abstract fun getDebugProfile(
        sessionId: String,
        projectName: String,
        projectPath: Path,
        dotnetExecutable: DotNetExecutable,
        dotnetRuntime: DotNetCoreRuntime,
        browserSettings: StartBrowserSettings?,
        sessionProcessEventListener: ProcessListener,
        sessionProcessTerminatedListener: ProcessListener,
        sessionProcessLifetime: Lifetime
    ): RunProfile

    protected suspend fun getDotNetExecutable(
        sessionModel: SessionModel,
        hostRunConfiguration: AspireHostConfiguration?,
        addBrowserAction: Boolean,
        project: Project
    ): Pair<DotNetExecutable, StartBrowserSettings?>? {
        val factory = SessionExecutableFactory.getInstance(project)
        val executable = factory.createExecutable(sessionModel, hostRunConfiguration, addBrowserAction)
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

    protected suspend fun enableHotReload(
        executable: DotNetExecutable,
        sessionProjectPath: Path,
        launchProfile: String?,
        lifetime: Lifetime,
        project: Project
    ): Pair<DotNetExecutable, ProgramRunner.Callback?> {
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
}