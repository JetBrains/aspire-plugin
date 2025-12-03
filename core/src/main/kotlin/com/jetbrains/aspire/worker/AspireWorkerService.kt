package com.jetbrains.aspire.worker

import com.intellij.execution.RunManager
import com.intellij.execution.RunManagerListener
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.generated.AspireWorkerModel
import com.jetbrains.aspire.run.AspireConfigurationType
import com.jetbrains.aspire.run.AspireRunConfiguration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.nio.file.Path
import kotlin.io.path.absolutePathString

@Service(Service.Level.PROJECT)
internal class AspireWorkerService(private val project: Project) {
    companion object {
        fun getInstance(project: Project): AspireWorkerService = project.service()

        private val LOG = logger<AspireWorkerService>()
    }

    private val appHostPaths = java.util.concurrent.ConcurrentHashMap<Path, Unit>()

    private val _appHosts: MutableStateFlow<List<AspireAppHost>> = MutableStateFlow(emptyList())
    val appHosts: StateFlow<List<AspireAppHost>> = _appHosts.asStateFlow()

    private val _workerState: MutableStateFlow<AspireWorkerState> = MutableStateFlow(AspireWorkerState.Inactive)
    val workerState: StateFlow<AspireWorkerState> = _workerState.asStateFlow()

    private fun addAspireAppHost(mainFilePath: Path) {
        LOG.trace { "Adding a new Aspire AppHost ${mainFilePath.absolutePathString()}" }

        val previousValue = appHostPaths.putIfAbsent(mainFilePath, Unit)
        if (previousValue != null) return

        val newAppHost = AspireAppHost(mainFilePath, project)
        _appHosts.update { currentList ->
            currentList + newAppHost
        }
    }

    @Suppress("FoldInitializerAndIfToElvis")
    private fun removeAspireAppHost(mainFilePath: Path) {
        LOG.trace { "Removing the Aspire AppHost ${mainFilePath.absolutePathString()}" }

        val removed = appHostPaths.remove(mainFilePath)
        if (removed == null) return

        _appHosts.update { currentList ->
            currentList.filter { it.hostProjectPath != mainFilePath }
        }
    }

    sealed interface AspireWorkerState {
        object Inactive : AspireWorkerState
        data class Active(
            val debugSessionToken: String,
            val debugSessionPort: Int,
            val debugSessionServerCertificate: String?,
            val model: AspireWorkerModel
        ) : AspireWorkerState
    }

    class AspireRunConfigurationListener(private val project: Project) : RunManagerListener {
        override fun runConfigurationAdded(settings: RunnerAndConfigurationSettings) {
            val configuration = settings.configuration
            if (configuration !is AspireRunConfiguration) return

            val mainFilePath = configuration.parameters.mainFilePath

            val configurations = getAspireRunConfigurationsByMainFilePath(mainFilePath)
            if (configurations.size > 1) return

            getInstance(project).addAspireAppHost(Path.of(mainFilePath))
        }

        override fun runConfigurationRemoved(settings: RunnerAndConfigurationSettings) {
            val configuration = settings.configuration
            if (configuration !is AspireRunConfiguration) return

            val mainFilePath = configuration.parameters.mainFilePath

            val configurations = getAspireRunConfigurationsByMainFilePath(mainFilePath)
            if (configurations.isNotEmpty()) return

            getInstance(project).removeAspireAppHost(Path.of(mainFilePath))
        }

        private fun getAspireRunConfigurationsByMainFilePath(mainFilePath: String): List<AspireRunConfiguration> {
            val configurationType = ConfigurationTypeUtil.findConfigurationType(AspireConfigurationType::class.java)
            return RunManager.getInstance(project)
                .getConfigurationsList(configurationType)
                .filterIsInstance<AspireRunConfiguration>()
                .filter { it.parameters.mainFilePath == mainFilePath }
        }
    }
}