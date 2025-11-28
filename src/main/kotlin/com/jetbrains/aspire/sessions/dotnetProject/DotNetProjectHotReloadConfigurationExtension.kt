package com.jetbrains.aspire.sessions.dotnetProject

import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.aspire.sessions.projectLaunchers.AspireProjectHotReloadConfigurationExtension
import com.jetbrains.rider.run.configurations.HotReloadEnvironmentBuilder
import com.jetbrains.rider.run.configurations.HotReloadProgramRunnerCallback
import com.jetbrains.rider.run.configurations.RiderHotReloadRunConfigurationExtensionBase
import com.jetbrains.rider.run.configurations.RuntimeHotReloadRunConfigurationInfo
import com.jetbrains.rider.run.configurations.launchSettings.LaunchSettingsJson
import com.jetbrains.rider.runtime.DotNetExecutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class DotNetProjectHotReloadConfigurationExtension : RiderHotReloadRunConfigurationExtensionBase(),
    AspireProjectHotReloadConfigurationExtension {
    override suspend fun canExecute(
        lifetime: Lifetime,
        hotReloadRunInfo: RuntimeHotReloadRunConfigurationInfo,
        profile: LaunchSettingsJson.Profile?
    ): Boolean {
        if (profile?.hotReloadEnabled == false) return false

        return withContext(Dispatchers.EDT) { isRuntimeHotReloadAvailable(lifetime, hotReloadRunInfo) }
    }

    override suspend fun execute(
        executable: DotNetExecutable,
        lifetime: Lifetime,
        project: Project
    ): Pair<DotNetExecutable, ProgramRunner.Callback> {
        val pipeName = getPipeName()
        val hotReloadEnvs = HotReloadEnvironmentBuilder()
            .setNamedPipe(pipeName)
            .addDeltaApplier()
            .build()

        val envs = mergeEnvs(executable.environmentVariables, hotReloadEnvs)

        val modifiedExecutable = executable.copy(environmentVariables = envs)

        val callback = HotReloadProgramRunnerCallback(
            project,
            pipeName,
            null
        )

        return modifiedExecutable to callback
    }
}