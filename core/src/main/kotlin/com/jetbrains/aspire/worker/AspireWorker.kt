@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.worker

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.util.NetworkUtils
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.aspire.AspireService
import com.jetbrains.aspire.generated.AspireHostModel
import com.jetbrains.aspire.generated.AspireHostModelConfig
import com.jetbrains.aspire.generated.AspireWorkerModel
import com.jetbrains.aspire.generated.aspireWorkerModel
import com.jetbrains.aspire.settings.AspireSettings
import com.jetbrains.aspire.util.*
import com.jetbrains.rd.framework.*
import com.jetbrains.rd.protocol.IdeRootMarshallersProvider
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rd.util.lifetime.SequentialLifetimes
import com.jetbrains.rdclient.protocol.RdDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

/**
 * Domain service responsible for interaction with the AspireWorker.exe process.
 *
 * Key responsibilities:
 * - Starting and stopping the AspireWorker process ([start], [stop])
 * - Configuring the RD protocol for bidirectional communication with the process
 * - Managing the list of [AspireAppHost] instances and binding them to models from AspireWorker
 * - Providing environment variables for DCP connection to the IDE ([getEnvironmentVariablesForDcpConnection])
 *
 * @see AspireWorkerLauncher for process launching details
 * @see AspireWorkerModel for the RD model description
 */
@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class AspireWorker(private val project: Project, private val cs: CoroutineScope) {
    companion object {
        fun getInstance(project: Project): AspireWorker = project.service()
        private val LOG = logger<AspireWorker>()
    }

    private val _appHosts: MutableStateFlow<List<AspireAppHost>> = MutableStateFlow(emptyList())
    val appHosts: StateFlow<List<AspireAppHost>> = _appHosts.asStateFlow()

    private val _workerState: MutableStateFlow<AspireWorkerState> = MutableStateFlow(AspireWorkerState.Inactive)

    private val workerLifetimes = SequentialLifetimes(AspireService.getInstance(project).lifetime).apply {
        terminateCurrent()
    }
    private val mutex = Mutex()

    fun addAspireAppHost(mainFilePath: Path) {
        LOG.trace { "Adding a new Aspire AppHost ${mainFilePath.absolutePathString()}" }

        _appHosts.update { currentList ->
            if (currentList.any { it.mainFilePath == mainFilePath }) return@update currentList

            val appHost = AspireAppHost(mainFilePath, project, cs)
            currentList + appHost
        }
    }

    fun removeAspireAppHost(mainFilePath: Path) {
        LOG.trace { "Removing the Aspire AppHost ${mainFilePath.absolutePathString()}" }

        _appHosts.update { currentList ->
            currentList.filter { it.mainFilePath != mainFilePath }
        }
    }

    fun getAppHostByPath(mainFilePath: Path): AspireAppHost? {
        return _appHosts.value.firstOrNull { it.mainFilePath == mainFilePath }
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

            subscribeToAspireWorkerModel(protocol.aspireWorkerModel, workerLifetime.lifetime)

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

    private suspend fun subscribeToAspireWorkerModel(aspireWorkerModel: AspireWorkerModel, lifetime: Lifetime) {
        LOG.trace("Subscribing to Aspire worker model")

        withContext(Dispatchers.EDT) {
            aspireWorkerModel.aspireHosts.view(lifetime) { appHostLifetime, _, appHostModel ->
                viewAspireAppHost(appHostModel, appHostLifetime)
            }
        }
    }

    private fun viewAspireAppHost(appHostModel: AspireHostModel, appHostLifetime: Lifetime) {
        val appHostMainFilePath = Path(appHostModel.config.aspireHostProjectPath)
        val appHost = _appHosts.value.firstOrNull { it.mainFilePath == appHostMainFilePath }
        if (appHost == null) {
            LOG.warn("Could not find Aspire host $appHostMainFilePath")
            return
        }

        LOG.trace { "Setting Aspire host model to $appHostMainFilePath" }
        appHost.subscribeToAspireAppHostModel(appHostModel, appHostLifetime)
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

    @RequiresEdt
    fun startAspireHostModel(config: AspireHostModelConfig) {
        val state = _workerState.value
        if (state !is AspireWorkerState.Active) {
            LOG.warn("Unable to add Aspire host model because Session host isn't active")
            return
        }

        LOG.trace { "Adding Aspire host model $config" }
        state.model.aspireHosts[config.id] = AspireHostModel(config)
    }

    @RequiresEdt
    fun stopAspireHostModel(aspireHostId: String) {
        val state = _workerState.value
        if (state !is AspireWorkerState.Active) {
            LOG.warn("Unable to remove Aspire host model because Session host isn't active")
            return
        }

        LOG.trace { "Removing Aspire host model with id $aspireHostId" }
        state.model.aspireHosts.remove(aspireHostId)
    }

    sealed interface AspireWorkerState {
        data object Inactive : AspireWorkerState
        data class Active(
            val debugSessionToken: String,
            val debugSessionPort: Int,
            val debugSessionServerCertificate: String?,
            val model: AspireWorkerModel
        ) : AspireWorkerState
    }
}