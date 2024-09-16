package com.jetbrains.rider.aspire.sessionHost

import com.intellij.openapi.application.EDT
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.run.configurations.HotReloadEnvironmentBuilder
import com.jetbrains.rider.run.configurations.RiderHotReloadRunConfigurationExtensionBase
import com.jetbrains.rider.run.configurations.RuntimeHotReloadRunConfigurationInfo
import com.jetbrains.rider.run.configurations.launchSettings.LaunchSettingsJson
import com.jetbrains.rider.runtime.DotNetExecutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SessionHotReloadConfigurationExtension : RiderHotReloadRunConfigurationExtensionBase() {
    suspend fun canExecute(
        lifetime: Lifetime,
        hotReloadRunInfo: RuntimeHotReloadRunConfigurationInfo,
        profile: LaunchSettingsJson.Profile?
    ): Boolean {
        if (profile?.hotReloadEnabled == false) return false

        return withContext(Dispatchers.EDT) { isRuntimeHotReloadAvailable(lifetime, hotReloadRunInfo) }
    }

    fun execute(executable: DotNetExecutable): DotNetExecutable {
        val pipeName = getPipeName()
        val hotReloadEnvs = HotReloadEnvironmentBuilder()
            .setNamedPipe(pipeName)
            .addDeltaApplier()
            .build()

        val envs = mergeEnvs(executable.environmentVariables, hotReloadEnvs)

        return executable.copy(environmentVariables = envs)
    }
}