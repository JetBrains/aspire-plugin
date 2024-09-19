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
import com.jetbrains.rider.debugger.editAndContinue.web.BrowserRefreshAgentManager
import com.jetbrains.rider.debugger.editAndContinue.web.WebRuntimeHotReloadProcess
import com.jetbrains.rider.hotReload.HotReloadHost
import com.jetbrains.rider.run.configurations.HotReloadEnvironmentBuilder
import com.jetbrains.rider.run.configurations.RiderHotReloadRunConfigurationExtensionBase
import com.jetbrains.rider.run.configurations.RuntimeHotReloadRunConfigurationInfo
import com.jetbrains.rider.run.configurations.launchSettings.LaunchSettingsJson
import com.jetbrains.rider.run.kill
import com.jetbrains.rider.runtime.DotNetExecutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WasmHostHotReloadConfigurationExtension : RiderHotReloadRunConfigurationExtensionBase(),
    AspireProjectHotReloadConfigurationExtension {
    override suspend fun canExecute(
        lifetime: Lifetime,
        hotReloadRunInfo: RuntimeHotReloadRunConfigurationInfo,
        profile: LaunchSettingsJson.Profile?
    ): Boolean {
        if (profile?.hotReloadEnabled == false) return false
        if (HotReloadHost.getInstance(hotReloadRunInfo.project).blazorWasmHotReloadEnabled.valueOrNull != true) return false

        return withContext(Dispatchers.EDT) { isRuntimeHotReloadAvailable(lifetime, hotReloadRunInfo) }
    }

    override suspend fun execute(
        executable: DotNetExecutable,
        lifetime: Lifetime,
        project: Project
    ): Pair<DotNetExecutable, ProcessAdapter> {
        val pipeName = getPipeName()
        val browserRefreshHost = BrowserRefreshAgentManager
            .getInstance(project)
            .startHost(executable.projectTfm, lifetime)
        val hotReloadEnvs = HotReloadEnvironmentBuilder()
            .setNamedPipe(pipeName)
            .addDeltaApplier()
            .addBlazorRefreshClient()
            .setBlazorRefreshServerUrls(browserRefreshHost.wsUrls, browserRefreshHost.serverKey)
            .build()

        val envs = mergeEnvs(executable.environmentVariables, hotReloadEnvs)

        val modifiedExecutable = executable.copy(environmentVariables = envs)

        val hotReloadProcessLifetime = lifetime.createNested()
        val serverProcess = DotNetRunHotReloadProcess(hotReloadProcessLifetime, pipeName)
        val webProcess = WebRuntimeHotReloadProcess(project, browserRefreshHost, serverProcess)
        project.hotReloadManager.addProcess(webProcess)

        val adapter = object : ProcessAdapter() {
            override fun processTerminated(event: ProcessEvent) {
                application.invokeLater {
                    if (hotReloadProcessLifetime.isAlive) {
                        hotReloadProcessLifetime.terminate()
                    }

                    if (!browserRefreshHost.process.isProcessTerminating && !browserRefreshHost.process.isProcessTerminated) {
                        browserRefreshHost.process.kill()
                    }
                }
            }
        }

        return modifiedExecutable to adapter
    }
}