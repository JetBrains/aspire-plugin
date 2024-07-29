package me.rafaelldi.aspire.sessionHost

import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.run.configurations.HotReloadEnvironmentBuilder
import com.jetbrains.rider.run.configurations.RiderHotReloadRunConfigurationExtensionBase
import com.jetbrains.rider.run.configurations.RuntimeHotReloadRunConfigurationInfo
import com.jetbrains.rider.run.configurations.launchSettings.LaunchSettingsJson
import com.jetbrains.rider.runtime.DotNetExecutable

class SessionHotReloadConfigurationExtension : RiderHotReloadRunConfigurationExtensionBase() {
    suspend fun canExecute(
        lifetime: Lifetime,
        hotReloadRunInfo: RuntimeHotReloadRunConfigurationInfo,
        profile: LaunchSettingsJson.Profile?
    ): Boolean {
        if (profile?.hotReloadEnabled == false) return false

        return isRuntimeHotReloadAvailable(lifetime, hotReloadRunInfo)
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