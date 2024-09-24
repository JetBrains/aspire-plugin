package com.jetbrains.rider.aspire.sessionHost.hotReload

import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.util.application
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.isAlive
import com.jetbrains.rider.debugger.editAndContinue.DotNetRunHotReloadProcess
import com.jetbrains.rider.debugger.editAndContinue.hotReloadManager
import com.jetbrains.rider.run.configurations.HotReloadEnvironmentBuilder
import com.jetbrains.rider.run.configurations.RiderHotReloadRunConfigurationExtensionBase
import com.jetbrains.rider.run.configurations.RuntimeHotReloadRunConfigurationInfo
import com.jetbrains.rider.run.configurations.launchSettings.LaunchSettingsJson
import com.jetbrains.rider.runtime.DotNetExecutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DotNetProjectHotReloadConfigurationExtension : RiderHotReloadRunConfigurationExtensionBase(),
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
    ): Pair<DotNetExecutable, ProcessAdapter> {
        val pipeName = getPipeName()
        val hotReloadEnvs = HotReloadEnvironmentBuilder()
            .setNamedPipe(pipeName)
            .addDeltaApplier()
            .build()

        val envs = mergeEnvs(executable.environmentVariables, hotReloadEnvs)

        val modifiedExecutable = executable.copy(environmentVariables = envs)

        val hotReloadProcessLifetime = lifetime.createNested()
        val runProcess = DotNetRunHotReloadProcess(hotReloadProcessLifetime, pipeName)
        project.hotReloadManager.addProcess(runProcess)

        val adapter = object : ProcessAdapter() {
            override fun processTerminated(event: ProcessEvent) {
                application.invokeLater {
                    if (hotReloadProcessLifetime.isAlive) {
                        hotReloadProcessLifetime.terminate()
                    }
                }
            }
        }

        return modifiedExecutable to adapter
    }
}