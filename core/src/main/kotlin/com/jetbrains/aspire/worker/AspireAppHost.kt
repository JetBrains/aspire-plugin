@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.worker

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.messages.impl.subscribeAsFlow
import com.jetbrains.aspire.AspireService
import com.jetbrains.aspire.sessions.*
import com.jetbrains.aspire.worker.dcp.AspireSessionServer
import com.jetbrains.aspire.worker.dcp.AspireSessionServerEndpoint
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Domain object representing an Aspire AppHost project.
 *
 * This class does not start or stop the AppHost process directly, as this is handled
 * separately through run configurations. Instead, it subscribes to [AppHostListener] events
 * to track the AppHost lifecycle state ([appHostState]).
 *
 * @param mainFilePath path to the main project file (.csproj or .cs) of the AppHost
 */
@ApiStatus.Internal
class AspireAppHost(
    val name: String,
    val mainFilePath: Path,
    private val project: Project,
    parentCs: CoroutineScope
) : Disposable, AspireSessionHost, AspireAppHostModel {
    companion object {
        private val LOG = logger<AspireAppHost>()
    }

    private val cs = parentCs.childScope("Aspire AppHost")

    override val appHostId: AspireAppHostId = mainFilePath.toAspireAppHostId()

    override val sessionEvents: ReceiveChannel<SessionEvent>
        field = Channel<SessionEvent>(Channel.UNLIMITED)

    private val hostLifetime = AspireService.getInstance(project).lifetime.createNested()

    private val sessionServerMutex = Mutex()
    private var sessionServer: AspireSessionServer? = null

    private val disposed = AtomicBoolean(false)

    val dcpInstancePrefix = generateDcpInstancePrefix()

    private val resourceTreeManager = ResourceTreeManager(mainFilePath, project, cs, this)
    private val otlpProxyManager = AppHostOtlpProxyManager(cs)

    override val rootResources: StateFlow<List<AspireResource>>
        get() = resourceTreeManager.rootResources

    @OptIn(ExperimentalCoroutinesApi::class)
    override val aspireDashboardUrl: StateFlow<String?> = rootResources
        .map { resources -> resources.firstOrNull { it.displayName == "aspire-dashboard" } }
        .flatMapLatest { resource ->
            resource?.data?.map { selectDashboardUrl(it.urls) } ?: flowOf(null)
        }
        .stateIn(cs, SharingStarted.Eagerly, null)

    private val appHostLifecycleEvents: SharedFlow<AppHostLifecycleEvent> =
        project.messageBus.subscribeAsFlow(AppHostListener.TOPIC) {
            object : AppHostListener {
                override fun appHostStarting(appHostFile: Path, environment: AppHostEnvironment) {
                    if (mainFilePath != appHostFile) return

                    LOG.trace { "Aspire AppHost $mainFilePath is starting" }
                    trySend(AppHostLifecycleEvent.Starting(environment))
                }

                override fun appHostStarted(
                    appHostFile: Path,
                    runConfigName: String?,
                    logFlow: SharedFlow<AppHostLogEntry>
                ) {
                    if (mainFilePath != appHostFile) return

                    LOG.trace { "Aspire AppHost $mainFilePath was started" }
                    trySend(AppHostLifecycleEvent.Started(runConfigName, logFlow))
                }

                override fun appHostStopped(appHostFile: Path) {
                    if (mainFilePath != appHostFile) return
                    LOG.trace { "Aspire AppHost $mainFilePath was stopped" }

                    trySend(AppHostLifecycleEvent.Stopped)
                }
            }
        }.shareIn(cs, SharingStarted.Eagerly)

    override val logFlow: StateFlow<SharedFlow<AppHostLogEntry>?> =
        appHostLifecycleEvents
            .map { (it as? AppHostLifecycleEvent.Started)?.logFlow }
            .stateIn(cs, SharingStarted.Eagerly, null)

    val appHostState =
        appHostLifecycleEvents.scan(AspireAppHostState.Inactive as AspireAppHostState) { previousState, event ->
            when (event) {
                is AppHostLifecycleEvent.Starting -> AspireAppHostState.Starting(event.environment)

                is AppHostLifecycleEvent.Started -> {
                    val environment = (previousState as? AspireAppHostState.Starting)?.environment
                    if (environment == null) {
                        LOG.warn("Aspire AppHost $mainFilePath started without a preceding Starting state")
                    }

                    AspireAppHostState.Started(
                        event.runConfigName,
                        environment ?: AppHostEnvironment.EMPTY
                    )
                }

                AppHostLifecycleEvent.Stopped -> AspireAppHostState.Stopped
            }
        }.stateIn(cs, SharingStarted.Eagerly, AspireAppHostState.Inactive)

    override val data: StateFlow<AspireAppHostData> = appHostState
        .map { state -> toData(state) }
        .stateIn(cs, SharingStarted.Eagerly, toData(appHostState.value))

    init {
        otlpProxyManager.observeAppHostState(appHostState)
        resourceTreeManager.observeAppHostState(appHostState)
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

            val server = AspireSessionServer(this, project)
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

    override fun createSession(createSessionRequest: CreateSessionRequest): CreateSessionResponse {
        val appHostStartedState = appHostState.value as? AspireAppHostState.Started

        val sessionId = UUID.randomUUID().toString()

        LOG.trace { "Creating Aspire session with id: $sessionId" }

        val request = StartSessionRequest(
            sessionId,
            createSessionRequest.launchConfiguration,
            sessionEvents,
            appHostStartedState?.runConfigName,
            hostLifetime.createNested()
        )

        SessionManager.getInstance(project).submitRequest(request)

        return CreateSessionResponse(sessionId, null)
    }

    override fun deleteSession(deleteSessionRequest: DeleteSessionRequest): DeleteSessionResponse {
        LOG.trace { "Deleting Aspire session with id: ${deleteSessionRequest.sessionId}" }

        val request = StopSessionRequest(deleteSessionRequest.sessionId)

        SessionManager.getInstance(project).submitRequest(request)

        return DeleteSessionResponse(deleteSessionRequest.sessionId, null)
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

        hostLifetime.terminate()
        cs.cancel()
    }

    private fun generateDcpInstancePrefix(): String {
        val allowedChars = buildList {
            addAll('A'..'Z')
            addAll('a'..'z')
            addAll('0'..'9')
        }
        return (1..5)
            .map { allowedChars.random() }
            .joinToString("")
    }

    private fun selectDashboardUrl(urls: List<ResourceUrl>): String? {
        val dashboardUrls = urls.filter { it.displayName.contains("dashboard", ignoreCase = true) }
        return dashboardUrls.firstOrNull { it.fullUrl.startsWith("https://", ignoreCase = true) }?.fullUrl
            ?: dashboardUrls.firstOrNull { it.fullUrl.startsWith("http://", ignoreCase = true) }?.fullUrl
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

    private sealed interface AppHostLifecycleEvent {
        data class Starting(
            val environment: AppHostEnvironment
        ) : AppHostLifecycleEvent

        data class Started(
            val runConfigName: String?,
            val logFlow: SharedFlow<AppHostLogEntry>,
        ) : AppHostLifecycleEvent

        data object Stopped : AppHostLifecycleEvent
    }

    sealed interface AspireAppHostState {
        data object Inactive : AspireAppHostState

        data class Starting(
            val environment: AppHostEnvironment
        ) : AspireAppHostState

        data class Started(
            val runConfigName: String?,
            val environment: AppHostEnvironment,
        ) : AspireAppHostState

        data object Stopped : AspireAppHostState
    }
}
