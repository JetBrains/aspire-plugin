package com.jetbrains.rider.aspire.run

import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rd.util.lifetime.isNotAlive
import com.jetbrains.rider.aspire.dashboard.AspireHost
import com.jetbrains.rider.aspire.run.host.AspireHostConfiguration
import com.jetbrains.rider.aspire.run.host.AspireHostConfigurationType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.Path

@Service(Service.Level.PROJECT)
class AspireHostRunManager(private val project: Project) {
    companion object {
        fun getInstance(project: Project) = project.service<AspireHostRunManager>()

        private val LOG = logger<AspireHostRunManager>()
    }

    private val configurationNames = ConcurrentHashMap<Path, String>()
    private val configurationLifetimes = ConcurrentHashMap<Path, LifetimeDefinition>()

    fun saveRunConfiguration(
        aspireHostProjectPath: Path,
        aspireHostLifetime: LifetimeDefinition,
        configurationName: String
    ) {
        configurationNames[aspireHostProjectPath] = configurationName
        configurationLifetimes[aspireHostProjectPath] = aspireHostLifetime
    }

    fun executeConfigurationForHost(host: AspireHost, underDebug: Boolean) {
        val executor =
            if (underDebug) DefaultDebugExecutor.getDebugExecutorInstance()
            else DefaultRunExecutor.getRunExecutorInstance()

        val runManager = RunManager.getInstance(project)
        val selected = runManager.selectedConfiguration
        val selectedConfiguration = selected?.configuration
        if (selectedConfiguration != null && selectedConfiguration is AspireHostConfiguration) {
            if (host.hostProjectPath == Path(selectedConfiguration.parameters.projectFilePath)) {
                ProgramRunnerUtil.executeConfiguration(selected, executor)
                return
            }
        }

        val configurations = runManager.getConfigurationSettingsList(AspireHostConfigurationType::class.java)
            .filter {
                val path = (it.configuration as? AspireHostConfiguration)?.parameters?.projectFilePath
                    ?: return@filter false
                Path(path) == host.hostProjectPath
            }

        if (configurations.isEmpty()) {
            LOG.warn("Unable to find Aspire host run configurations")
            return
        }

        val configurationName = configurationNames[host.hostProjectPath]
        if (configurationName != null) {
            val configurationWithName = configurations.firstOrNull { it.name == configurationName }
            if (configurationWithName != null) {
                ProgramRunnerUtil.executeConfiguration(configurationWithName, executor)
                return
            }
        }

        val firstConfiguration = configurations.first()
        ProgramRunnerUtil.executeConfiguration(firstConfiguration, executor)
    }

    suspend fun stopConfigurationForHost(host: AspireHost) {
        val lifetime = configurationLifetimes[host.hostProjectPath]
        if (lifetime == null || lifetime.lifetime.isNotAlive) {
            LOG.warn("Unable to stop configuration for Aspire host ${host.hostProjectPathString}")
            return
        }

        withContext(Dispatchers.EDT) {
            lifetime.terminate()
        }
    }
}