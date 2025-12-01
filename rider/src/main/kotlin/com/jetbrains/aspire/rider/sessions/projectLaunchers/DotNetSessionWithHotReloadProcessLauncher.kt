package com.jetbrains.aspire.rider.sessions.projectLaunchers

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.aspire.sessions.findBySessionProject
import com.jetbrains.rider.model.runnableProjectsModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.run.configurations.RunnableProjectKinds
import com.jetbrains.rider.run.configurations.RuntimeHotReloadRunConfigurationInfo
import com.jetbrains.rider.run.configurations.launchSettings.LaunchSettingsJsonService
import com.jetbrains.rider.runtime.DotNetExecutable
import java.nio.file.Path

/**
 * An implementation of the `SessionProcessLauncherExtension` interface that modifies created [DotNetExecutable]
 * to apply a Hot Reload mechanism.
 */
abstract class DotNetSessionWithHotReloadProcessLauncher: DotNetSessionProcessLauncher() {
    protected abstract val hotReloadExtension: AspireProjectHotReloadConfigurationExtension

    override suspend fun modifyDotNetExecutable(
        executable: DotNetExecutable,
        sessionProjectPath: Path,
        launchProfile: String?,
        lifetime: Lifetime,
        project: Project
    ): Pair<DotNetExecutable, ProgramRunner.Callback?> {
        val runnableProject =
            project.solution.runnableProjectsModel.findBySessionProject(sessionProjectPath) { it.kind == RunnableProjectKinds.DotNetCore }
                ?: return executable to null

        val hotReloadRunInfo = RuntimeHotReloadRunConfigurationInfo(
            DefaultRunExecutor.EXECUTOR_ID,
            project,
            runnableProject,
            executable.projectTfm,
            null
        )

        val profile = if (!launchProfile.isNullOrEmpty()) {
            LaunchSettingsJsonService
                .getInstance(project)
                .loadLaunchSettingsSuspend(runnableProject)
                ?.let { it.profiles?.get(launchProfile) }
        } else null

        if (!hotReloadExtension.canExecute(lifetime, hotReloadRunInfo, profile)) {
            return executable to null
        }

        return hotReloadExtension.execute(executable, lifetime, project)
    }
}