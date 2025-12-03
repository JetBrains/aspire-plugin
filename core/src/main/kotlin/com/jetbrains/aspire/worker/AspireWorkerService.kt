package com.jetbrains.aspire.worker

import com.intellij.execution.RunManager
import com.intellij.execution.RunManagerListener
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.util.NetworkUtils
import com.jetbrains.aspire.AspireService
import com.jetbrains.aspire.generated.AspireHostModel
import com.jetbrains.aspire.generated.AspireWorkerModel
import com.jetbrains.aspire.generated.aspireWorkerModel
import com.jetbrains.aspire.run.AspireConfigurationType
import com.jetbrains.aspire.run.AspireRunConfiguration
import com.jetbrains.aspire.settings.AspireSettings
import com.jetbrains.aspire.util.DEBUG_SESSION_PORT
import com.jetbrains.aspire.util.DEBUG_SESSION_SERVER_CERTIFICATE
import com.jetbrains.aspire.util.DEBUG_SESSION_TOKEN
import com.jetbrains.aspire.util.checkDevCertificate
import com.jetbrains.aspire.util.exportCertificate
import com.jetbrains.rd.framework.*
import com.jetbrains.rd.protocol.IdeRootMarshallersProvider
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rd.util.lifetime.SequentialLifetimes
import com.jetbrains.rdclient.protocol.RdDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.util.*
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

    private val workerLifetimes = SequentialLifetimes(AspireService.getInstance(project).lifetime).apply {
        terminateCurrent()
    }
    private val mutex = Mutex()

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
            currentList.filter { it.mainFilePath != mainFilePath }
        }
    }

    //Switch DCP to the IDE mode
    //see: https://github.com/dotnet/aspire/blob/main/docs/specs/IDE-execution.md#enabling-ide-execution
    fun getEnvironmentVariablesForDcpConnection(): Map<String, String> {
        val state = _workerState.value
        if (state !is AspireWorkerState.Active) return emptyMap()

        return buildMap {
            val debugSessionToken = state.debugSessionToken
            val debugSessionPort = state.debugSessionPort
            val debugSessionServerCertificate = state.debugSessionServerCertificate

            put(DEBUG_SESSION_TOKEN, debugSessionToken)
            put(DEBUG_SESSION_PORT, "localhost:$debugSessionPort")
            debugSessionServerCertificate?.let { put(DEBUG_SESSION_SERVER_CERTIFICATE, it) }
        }
    }

    suspend fun start() {
        if (!workerLifetimes.isTerminated) return

        mutex.withLock {
            if (!workerLifetimes.isTerminated) return

            LOG.trace("Starting Aspire worker")
            val workerLifetime = workerLifetimes.next()

            val protocol = startAspireWorkerProtocol(workerLifetime.lifetime)

            subscribeToModel(protocol.aspireWorkerModel, workerLifetime.lifetime)

            val token = UUID.randomUUID().toString()
            val port = NetworkUtils.findFreePort(47100)
            val certificate = calculateServerCertificate(workerLifetime)
            val model = protocol.aspireWorkerModel

            val aspireWorkerConfig = AspireWorkerConfig(
                protocol.wire.serverPort,
                token,
                port,
                certificate != null
            )

            LOG.trace { "Starting a new session host with launcher $aspireWorkerConfig" }
            val aspireWorkerLauncher = AspireWorkerLauncher.getInstance(project)
            aspireWorkerLauncher.launchWorker(aspireWorkerConfig, workerLifetime)

            _workerState.value = AspireWorkerState.Active(
                debugSessionToken = token,
                debugSessionPort = port,
                debugSessionServerCertificate = certificate,
                model = model
            )
        }
    }

    private suspend fun startAspireWorkerProtocol(lifetime: Lifetime) = withContext(Dispatchers.EDT) {
        val dispatcher = RdDispatcher(lifetime)
        val wire = SocketWire.Server(lifetime, dispatcher, null)
        val protocol = Protocol(
            "AspireWorker::protocol",
            Serializers(IdeRootMarshallersProvider),
            Identities(IdKind.Server),
            dispatcher,
            wire,
            lifetime
        )
        return@withContext protocol
    }

    private suspend fun subscribeToModel(aspireWorkerModel: AspireWorkerModel, lifetime: Lifetime) {
        LOG.trace("Subscribing to Aspire worker model")

        withContext(Dispatchers.EDT) {
            aspireWorkerModel.aspireHosts.view(lifetime) { appHostLifetime, _, appHostModel ->
                viewAspireAppHost(appHostModel, appHostLifetime)
            }
        }
    }

    private fun viewAspireAppHost(appHostModel: AspireHostModel, appHostLifetime: Lifetime) {
        // Empty for now
    }

    private suspend fun calculateServerCertificate(workerLifetime: LifetimeDefinition): String? {
        if (!AspireSettings.getInstance().connectToDcpViaHttps) return null

        val hasTrustedCertificate = checkDevCertificate(workerLifetime, project)
        if (!hasTrustedCertificate) return null

        return exportCertificate(workerLifetime, project)
    }

    suspend fun stop() {
        if (workerLifetimes.isTerminated) return

        mutex.withLock {
            if (workerLifetimes.isTerminated) return

            LOG.trace("Stopping Aspire worker")

            _workerState.value = AspireWorkerState.Inactive

            workerLifetimes.terminateCurrent()
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