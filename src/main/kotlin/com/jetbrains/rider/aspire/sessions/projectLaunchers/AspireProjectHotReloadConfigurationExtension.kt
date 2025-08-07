package com.jetbrains.rider.aspire.sessions.projectLaunchers

import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.run.configurations.RuntimeHotReloadRunConfigurationInfo
import com.jetbrains.rider.run.configurations.launchSettings.LaunchSettingsJson
import com.jetbrains.rider.runtime.DotNetExecutable

interface AspireProjectHotReloadConfigurationExtension {
    suspend fun canExecute(
        lifetime: Lifetime,
        hotReloadRunInfo: RuntimeHotReloadRunConfigurationInfo,
        profile: LaunchSettingsJson.Profile?
    ): Boolean

    suspend fun execute(
        executable: DotNetExecutable,
        lifetime: Lifetime,
        project: Project
    ): Pair<DotNetExecutable, ProgramRunner.Callback>
}