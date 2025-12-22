package com.jetbrains.aspire.rider.run

import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.worker.AspireAppHost
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rd.util.lifetime.isNotAlive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

@Service(Service.Level.PROJECT)
class AspireRunConfigurationManager(private val project: Project) {
    companion object {
        fun getInstance(project: Project) = project.service<AspireRunConfigurationManager>()

        private val LOG = logger<AspireRunConfigurationManager>()
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

    fun executeConfigurationForHost(appHost: AspireAppHost, underDebug: Boolean) {
        val executor =
            if (underDebug) DefaultDebugExecutor.getDebugExecutorInstance()
            else DefaultRunExecutor.getRunExecutorInstance()

        val runManager = RunManager.getInstance(project)
        val selected = runManager.selectedConfiguration
        val selectedConfiguration = selected?.configuration
        if (selectedConfiguration != null && selectedConfiguration is AspireRunConfiguration) {
            if (appHost.mainFilePath == Path(selectedConfiguration.parameters.mainFilePath)) {
                ProgramRunnerUtil.executeConfiguration(selected, executor)
                return
            }
        }

        val configurations = runManager.getConfigurationSettingsList(AspireConfigurationType::class.java)
            .filter {
                val configuration = it.configuration
                if (configuration !is AspireRunConfiguration) return@filter false
                Path(configuration.parameters.mainFilePath) == appHost.mainFilePath
            }

        if (configurations.isEmpty()) {
            LOG.warn("Unable to find any Aspire run configurations with the given host path")
            return
        }

        val configurationName = configurationNames[appHost.mainFilePath]
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

    suspend fun stopConfigurationForHost(appHost: AspireAppHost) {
        val lifetime = configurationLifetimes[appHost.mainFilePath]
        if (lifetime == null || lifetime.lifetime.isNotAlive) {
            LOG.warn("Unable to stop configuration for Aspire host ${appHost.mainFilePath.absolutePathString()}")
            return
        }

        withContext(Dispatchers.EDT) {
            lifetime.terminate()
        }
    }
}