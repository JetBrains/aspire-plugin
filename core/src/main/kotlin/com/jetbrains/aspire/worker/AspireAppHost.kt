@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.worker

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.jetbrains.aspire.sessions.SessionManagerImpl
import com.jetbrains.aspire.dcp.AspireSessionServer
import com.jetbrains.aspire.dcp.AspireSessionServerEndpoint
import com.jetbrains.aspire.resources.AspireResourceClient
import com.jetbrains.aspire.resources.grpc.GrpcResourceClientFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Domain object representing an Aspire AppHost project.
 *
 * This class does not start or stop the AppHost process directly, as this is handled
 * separately through run configurations. Instead, it subscribes to [AppHostListener] events
 * to track the AppHost lifecycle state ([appHostState]) and owns the resource client
 * supplied to [ResourceTreeManager].
 *
 * @param mainFilePath path to the main project file (.csproj or .cs) of the AppHost
 */
@ApiStatus.Internal
class AspireAppHost(
    override val id: AspireAppHostId,
    val name: String,
    val mainFilePath: Path,
    private val project: Project,
    parentCs: CoroutineScope
) : Disposable, AspireAppHostModel {
    companion object {
        private val LOG = logger<AspireAppHost>()
    }

    private val cs = parentCs.childScope("Aspire AppHost")

    override val appHostPath: AspireAppHostPath = mainFilePath.toAspireAppHostPath()

    private val sessionServerMutex = Mutex()
    private var sessionServer: AspireSessionServer? = null

    private val otlpProxyManager = AppHostOtlpProxyManager(cs)
    private val sessionManager = SessionManagerImpl(project, cs, mainFilePath)
        .also { Disposer.register(this, it) }

    private val disposed = AtomicBoolean(false)

    private val mutableLogFlow = MutableStateFlow<SharedFlow<AppHostLogEntry>?>(null)
    override val logFlow: StateFlow<SharedFlow<AppHostLogEntry>?> = mutableLogFlow.asStateFlow()

    private val mutableAppHostState = MutableStateFlow<AspireAppHostState>(AspireAppHostState.Inactive)
    val appHostState: StateFlow<AspireAppHostState> = mutableAppHostState.asStateFlow()

    private val resourceClient = MutableStateFlow<AspireResourceClient?>(null)
    private val resourceTreeManager = ResourceTreeManager(mainFilePath, project, cs, this, resourceClient)
    override val rootResources: StateFlow<List<AspireResource>>
        get() = resourceTreeManager.rootResources

    @OptIn(ExperimentalCoroutinesApi::class)
    override val aspireDashboardUrl: StateFlow<String?> = rootResources
        .map { resources -> resources.firstOrNull { it.displayName == "aspire-dashboard" } }
        .flatMapLatest { resource ->
            resource?.data?.map { selectDashboardUrl(it.urls) } ?: flowOf(null)
        }
        .stateIn(cs, SharingStarted.Eagerly, null)

    override val data: StateFlow<AspireAppHostData> = appHostState
        .map { state -> toData(state) }
        .stateIn(cs, SharingStarted.Eagerly, toData(appHostState.value))

    init {
        project.messageBus
            .connect(cs)
            .subscribe(AppHostListener.TOPIC, AppHostLifecycleListener())
        otlpProxyManager.observeAppHostState(appHostState)
        launchResourceClientLifecycle()
    }

    private fun launchResourceClientLifecycle() {
        cs.launch {
            appHostState.collectLatest { state ->
                val environment = (state as? AspireAppHostState.Started)?.environment ?: return@collectLatest
                val endpointUrl = environment.resourceServiceEndpointUrl
                if (endpointUrl == null) {
                    LOG.trace { "Aspire AppHost $mainFilePath started without a resource service endpoint" }
                    return@collectLatest
                }

                LOG.trace { "Initializing gRPC dashboard client for $mainFilePath" }
                val client = service<GrpcResourceClientFactory>()
                    .create(endpointUrl, environment.resourceServiceApiKey)
                try {
                    resourceClient.value = client
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) {
                        resourceClient.value = null
                        try {
                            resourceTreeManager.awaitResourceObservationStopped()
                        } finally {
                            client.shutdown()
                        }
                    }
                }
            }
        }
    }

    /**
     * Starts this host's embedded DCP session server (idempotent) and suspends until it is bound, so the
     * AppHost process — which connects immediately on launch — can be started only after this returns.
     * A second call while running returns the already-bound endpoint.
     */
    suspend fun startSessionServer(): AspireSessionServerEndpoint {
        sessionServerMutex.withLock {
            check(!disposed.get()) { "AspireAppHost is disposed" }

            sessionServer?.let { return checkNotNull(it.endpoint) }

            val server = AspireSessionServer(sessionManager, project)
            server.start()

            if (disposed.get()) {
                withContext(NonCancellable) { server.stop() }
                error("AspireAppHost is disposed")
            }

            sessionServer = server
            val endpoint = checkNotNull(server.endpoint)
            LOG.trace { "Started embedded DCP server for $mainFilePath on port ${endpoint.port} (https=${endpoint.isHttps})" }
            return endpoint
        }
    }

    override fun dispose() {
        if (!disposed.compareAndSet(false, true)) return

        LOG.trace { "Disposing AspireAppHost for project: $mainFilePath" }

        cs.launch(start = CoroutineStart.ATOMIC) {
            withContext(NonCancellable) {
                sessionServerMutex.withLock {
                    try {
                        sessionServer?.stop()
                    } finally {
                        sessionServer = null
                    }
                }
            }
        }

        cs.cancel()
    }

    private fun selectDashboardUrl(urls: List<ResourceUrl>): String? {
        val dashboardUrls = urls.filter { it.displayName.contains("dashboard", ignoreCase = true) }
        return dashboardUrls.firstOrNull { it.fullUrl.startsWith("https://", ignoreCase = true) }?.fullUrl
            ?: dashboardUrls.firstOrNull { it.fullUrl.startsWith("http://", ignoreCase = true) }?.fullUrl
    }

    private inner class AppHostLifecycleListener : AppHostListener {
        override fun appHostStarting(appHostFile: Path, environment: AppHostEnvironment) {
            if (mainFilePath != appHostFile) return

            LOG.trace { "Aspire AppHost $mainFilePath is starting" }
            mutableLogFlow.value = null
            mutableAppHostState.value = AspireAppHostState.Starting(environment)
        }

        override fun appHostStarted(appHostFile: Path, logFlow: SharedFlow<AppHostLogEntry>) {
            if (mainFilePath != appHostFile) return

            LOG.trace { "Aspire AppHost $mainFilePath was started" }
            mutableLogFlow.value = logFlow
            mutableAppHostState.update { previousState ->
                val environment = (previousState as? AspireAppHostState.Starting)?.environment
                if (environment == null) {
                    LOG.warn("Aspire AppHost $mainFilePath started without a preceding Starting state")
                }

                AspireAppHostState.Started(environment ?: AppHostEnvironment.EMPTY)
            }
        }

        override fun appHostStopped(appHostFile: Path) {
            if (mainFilePath != appHostFile) return

            LOG.trace { "Aspire AppHost $mainFilePath was stopped" }
            mutableLogFlow.value = null
            mutableAppHostState.value = AspireAppHostState.Stopped
        }
    }

    data class AppHostEnvironment(
        val resourceServiceEndpointUrl: String?,
        val resourceServiceApiKey: String?,
        val otlpEndpointUrl: String?
    ) {
        companion object {
            val EMPTY = AppHostEnvironment(null, null, null)
        }
    }

    sealed interface AspireAppHostState {
        data object Inactive : AspireAppHostState

        data class Starting(
            val environment: AppHostEnvironment
        ) : AspireAppHostState

        data class Started(
            val environment: AppHostEnvironment,
        ) : AspireAppHostState

        data object Stopped : AspireAppHostState
    }
}
