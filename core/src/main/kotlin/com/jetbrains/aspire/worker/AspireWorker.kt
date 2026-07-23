@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.worker

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.NetworkUtils
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.aspire.AspireService
import com.jetbrains.aspire.extensions.DevCertificateProvider
import com.jetbrains.aspire.generated.*
import com.jetbrains.aspire.settings.AspireSettings
import com.jetbrains.aspire.util.*
import com.jetbrains.aspire.worker.dcp.AspireSessionHost
import com.jetbrains.aspire.worker.dcp.AspireSessionServer
import com.jetbrains.aspire.worker.dcp.AspireSessionServerConfig
import com.jetbrains.aspire.worker.dcp.AspireSessionTlsConfig
import com.jetbrains.rd.framework.*
import com.jetbrains.rd.protocol.IdeRootMarshallersProvider
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rd.util.lifetime.SequentialLifetimes
import com.jetbrains.rd.util.threading.coroutines.asCoroutineDispatcher
import com.jetbrains.rdclient.protocol.RdDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.util.*
import kotlin.io.path.absolutePathString
import kotlin.io.path.nameWithoutExtension

/**
 * Domain service responsible for DCP session hosting.
 *
 * Key responsibilities:
 * - Starting and stopping either the external AspireWorker process or the embedded Ktor server ([start], [stop])
 * - Configuring the RD protocol for the external worker transport
 * - Delegating DCP session create/delete requests to the matching [AspireAppHost]
 * - Managing the list of [AspireAppHost] instances and binding them to models from AspireWorker
 * - Providing environment variables for DCP connection to the IDE ([getEnvironmentVariablesForDcpConnection])
 *
 * @see AspireWorkerLauncher for process launching details
 * @see AspireWorkerModel for the RD model description
 */
@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class AspireWorker(private val project: Project, private val cs: CoroutineScope) : Disposable, AspireSessionHost {
    companion object {
        fun getInstance(project: Project): AspireWorker = project.service()
        private val LOG = logger<AspireWorker>()

        private const val EMBEDDED_SESSION_HOST_REGISTRY_KEY = "aspire.embedded.session.host"
    }

    private val _appHosts: MutableStateFlow<List<AspireAppHost>> = MutableStateFlow(emptyList())
    val appHosts: StateFlow<List<AspireAppHost>> = _appHosts.asStateFlow()

    private val _workerState: MutableStateFlow<AspireWorkerState> = MutableStateFlow(AspireWorkerState.Inactive)

    private val workerLifetimes = SequentialLifetimes(AspireService.getInstance(project).lifetime).apply {
        terminateCurrent()
    }
    private val mutex = Mutex()

    private fun addAppHost(name: String, appHostFilePath: Path) {
        LOG.trace { "Adding a new Aspire AppHost ${appHostFilePath.absolutePathString()}" }

        _appHosts.update { currentList ->
            if (currentList.any { it.mainFilePath == appHostFilePath }) return@update currentList

            val appHost = AspireAppHost(name, appHostFilePath, project, cs)
            Disposer.register(this@AspireWorker, appHost)
            currentList + appHost
        }
    }

    private fun removeAppHost(appHostFilePath: Path) {
        LOG.trace { "Removing the Aspire AppHost ${appHostFilePath.absolutePathString()}" }

        _appHosts.update { currentList ->
            currentList.filter { it.mainFilePath != appHostFilePath }
        }
    }

    fun getAppHostByPath(appHostFilePath: Path): AspireAppHost? {
        return _appHosts.value.firstOrNull { it.mainFilePath == appHostFilePath }
    }

    fun getOrCreateAppHostByPath(appHostFilePath: Path): AspireAppHost? {
        _appHosts.value.firstOrNull { it.mainFilePath == appHostFilePath }?.let { return it }

        addAppHost(appHostFilePath.nameWithoutExtension, appHostFilePath)

        return _appHosts.value.firstOrNull { it.mainFilePath == appHostFilePath }
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
            val useEmbeddedSessionHost = Registry.`is`(EMBEDDED_SESSION_HOST_REGISTRY_KEY)
            val token = UUID.randomUUID().toString()

            try {
                if (useEmbeddedSessionHost) {
                    startEmbeddedSessionHost(workerLifetime, token)
                } else {
                    startExternalSessionHost(workerLifetime, token)
                }
            } catch (e: Exception) {
                workerLifetimes.terminateCurrent()
                throw e
            }
        }
    }

    private suspend fun startExternalSessionHost(workerLifetime: LifetimeDefinition, token: String) {
        val (protocol, dispatcher) = startAspireWorkerProtocol(workerLifetime.lifetime)
        subscribeToAspireWorkerModel(protocol.aspireWorkerModel, dispatcher, workerLifetime.lifetime)

        val port = NetworkUtils.findFreePort(47100)
        val endpointSecurity = prepareEndpointSecurity(requireTlsConfig = false)
        val aspireWorkerConfig = AspireWorkerConfig(
            protocol.wire.serverPort,
            token,
            port,
            endpointSecurity.debugSessionServerCertificate != null
        )

        LOG.trace { "Starting external Aspire session host with launcher $aspireWorkerConfig" }
        AspireWorkerLauncher.getInstance(project).launchWorker(aspireWorkerConfig, workerLifetime)

        _workerState.value = AspireWorkerState.Active(
            debugSessionToken = token,
            debugSessionPort = port,
            debugSessionServerCertificate = endpointSecurity.debugSessionServerCertificate,
            workerLifetime = workerLifetime,
            transport = AspireWorkerTransport.External(protocol.aspireWorkerModel),
        )
    }

    private suspend fun startEmbeddedSessionHost(workerLifetime: LifetimeDefinition, token: String) {
        val endpointSecurity = prepareEndpointSecurity(requireTlsConfig = true)
        val server = AspireSessionServer(
            this,
            AspireSessionServerConfig(port = 0, token = token, tls = endpointSecurity.tlsConfig)
        )
        workerLifetime.onTermination {
            cs.launch { server.stop() }
        }

        LOG.trace { "Starting embedded Aspire session host (https=${server.isHttps})" }
        server.start()

        _workerState.value = AspireWorkerState.Active(
            debugSessionToken = token,
            debugSessionPort = server.resolvedPort,
            debugSessionServerCertificate = endpointSecurity.debugSessionServerCertificate,
            workerLifetime = workerLifetime,
            transport = AspireWorkerTransport.Embedded(server),
        )
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
        return@withContext protocol to dispatcher
    }

    private suspend fun subscribeToAspireWorkerModel(
        aspireWorkerModel: AspireWorkerModel,
        dispatcher: RdDispatcher,
        lifetime: Lifetime
    ) {
        LOG.trace("Subscribing to Aspire worker model")

        withContext(dispatcher.asCoroutineDispatcher) {
            aspireWorkerModel.createSession.set { request ->
                createSession(request, lifetime)
            }

            aspireWorkerModel.deleteSession.set { request ->
                deleteSession(request)
            }

            aspireWorkerModel.aspireHosts.view(lifetime) { appHostLifetime, _, appHostModel ->
                viewAspireAppHost(appHostModel, dispatcher, appHostLifetime)
            }
        }
    }

    override fun createSession(createSessionRequest: CreateSessionRequest): CreateSessionResponse {
        val state = _workerState.value as? AspireWorkerState.Active
            ?: return CreateSessionResponse(null, ErrorCode.AspireSessionNotFound)

        return createSession(createSessionRequest, state.workerLifetime.lifetime)
    }

    private fun createSession(
        createSessionRequest: CreateSessionRequest,
        lifetime: Lifetime
    ): CreateSessionResponse {
        val host = appHostByDcpInstancePrefix(createSessionRequest.dcpInstancePrefix)
        if (host == null) {
            return CreateSessionResponse(null, ErrorCode.AspireSessionNotFound)
        }

        return host.createSession(createSessionRequest, lifetime)
    }

    override fun deleteSession(deleteSessionRequest: DeleteSessionRequest): DeleteSessionResponse {
        val host = appHostByDcpInstancePrefix(deleteSessionRequest.dcpInstancePrefix)
        if (host == null) {
            return DeleteSessionResponse(null, ErrorCode.AspireSessionNotFound)
        }

        return host.deleteSession(deleteSessionRequest)
    }

    override fun sessionEvents(dcpInstancePrefix: String) =
        appHostByDcpInstancePrefix(dcpInstancePrefix)?.sessionEvents

    private fun appHostByDcpInstancePrefix(dcpInstancePrefix: String) =
        _appHosts.value.singleOrNull { it.dcpInstancePrefix == dcpInstancePrefix }

    private fun viewAspireAppHost(
        appHostModel: AspireHostModel,
        dispatcher: RdDispatcher,
        appHostLifetime: Lifetime
    ) {
        val appHostMainFilePath = Path.of(appHostModel.config.aspireHostProjectPath)
        val appHost = _appHosts.value.firstOrNull { it.mainFilePath == appHostMainFilePath }
        if (appHost == null) {
            LOG.warn("Could not find Aspire host $appHostMainFilePath")
            return
        }

        LOG.trace { "Setting Aspire host model to $appHostMainFilePath" }
        appHost.subscribeToAspireAppHostModel(appHostModel, dispatcher, appHostLifetime)
    }

    private suspend fun prepareEndpointSecurity(requireTlsConfig: Boolean): EndpointSecurity {
        if (!AspireSettings.getInstance().connectToDcpViaHttps) return EndpointSecurity()

        val provider = DevCertificateProvider.getInstance() ?: return EndpointSecurity()
        val certificateCheckResult = provider.checkDevCertificate(true, project)
        if (!certificateCheckResult.isTrusted) return EndpointSecurity()

        val publicCertificate = provider.exportCertificate(true, project) ?: return EndpointSecurity()
        if (!requireTlsConfig) return EndpointSecurity(debugSessionServerCertificate = publicCertificate)

        val tlsConfig = provider.exportTlsConfig(true, project)
        if (tlsConfig == null) {
            LOG.trace("Unable to prepare development-certificate key material; using HTTP for embedded session host")
            return EndpointSecurity()
        }

        return EndpointSecurity(publicCertificate, tlsConfig)
    }

    suspend fun stop() {
        if (workerLifetimes.isTerminated) return

        mutex.withLock {
            if (workerLifetimes.isTerminated) return

            LOG.trace("Stopping Aspire worker")

            val state = _workerState.value as? AspireWorkerState.Active
            (state?.transport as? AspireWorkerTransport.Embedded)?.server?.stop()
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

        val externalTransport = state.transport as? AspireWorkerTransport.External ?: return

        LOG.trace { "Adding Aspire host model $config" }
        externalTransport.model.aspireHosts[config.id] = AspireHostModel(config)
    }

    @RequiresEdt
    fun stopAspireHostModel(aspireHostId: String) {
        val state = _workerState.value
        if (state !is AspireWorkerState.Active) {
            LOG.warn("Unable to remove Aspire host model because Session host isn't active")
            return
        }

        val externalTransport = state.transport as? AspireWorkerTransport.External ?: return

        LOG.trace { "Removing Aspire host model with id $aspireHostId" }
        externalTransport.model.aspireHosts.remove(aspireHostId)
    }

    override fun dispose() {
    }

    private sealed interface AspireWorkerState {
        data object Inactive : AspireWorkerState
        data class Active(
            val debugSessionToken: String,
            val debugSessionPort: Int,
            val debugSessionServerCertificate: String?,
            val workerLifetime: LifetimeDefinition,
            val transport: AspireWorkerTransport,
        ) : AspireWorkerState
    }

    private sealed interface AspireWorkerTransport {
        data class External(val model: AspireWorkerModel) : AspireWorkerTransport
        data class Embedded(val server: AspireSessionServer) : AspireWorkerTransport
    }

    private data class EndpointSecurity(
        val debugSessionServerCertificate: String? = null,
        val tlsConfig: AspireSessionTlsConfig? = null,
    )

    private class DetectionListener(private val project: Project) : AppHostDetectionListener {
        override fun appHostDetected(appHostName: String, appHostFilePath: Path) {
            getInstance(project).addAppHost(appHostName, appHostFilePath)
        }

        override fun appHostRemoved(appHostFilePath: Path) {
            getInstance(project).removeAppHost(appHostFilePath)
        }
    }
}
