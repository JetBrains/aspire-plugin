@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.worker

import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.messages.impl.subscribeAsFlow
import com.jetbrains.aspire.dashboard.ResourceListener
import com.jetbrains.aspire.generated.*
import com.jetbrains.aspire.generated.dashboard.InitialResourceData
import com.jetbrains.aspire.generated.dashboard.Resource as ProtoResource
import com.jetbrains.aspire.generated.dashboard.WatchResourcesChanges
import com.jetbrains.aspire.otlp.OpenTelemetryProtocolServerExtension
import com.jetbrains.aspire.sessions.*
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.debugger.DebuggerWorkerProcessHandler
import com.jetbrains.rider.run.ConsoleKind
import com.jetbrains.rider.run.createConsole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.Path

/**
 * Domain object representing an Aspire AppHost project.
 *
 * This class does not start or stop the AppHost process directly, as this is handled
 * separately through run configurations. Instead, it subscribes to [AppHostListener] events
 * to track the AppHost lifecycle state ([appHostState]).
 *
 * Key responsibilities:
 * - Handling session create/delete requests from Aspire DCP ([createSession], [deleteSession])
 * - Tracking resources running within this AppHost ([resources])
 * - Managing the dashboard URL and OTLP endpoint configuration
 * - Forwarding session events (started, terminated, log received) back to the AppHost model
 *
 * @param mainFilePath path to the main project file (.csproj or .cs) of the AppHost
 *
 * @see AspireWorker for the service that manages this object
 * @see SessionManager for session request processing
 */
@ApiStatus.Internal
class AspireAppHost(
    val name: String,
    val mainFilePath: Path,
    private val project: Project,
    parentCs: CoroutineScope
) : Disposable {
    companion object {
        private val LOG = logger<AspireAppHost>()

        private const val INITIAL_RETRY_DELAY_MS = 1_000L
        private const val MAX_RETRY_DELAY_MS = 5_000L
    }

    private val cs = parentCs.childScope("Aspire AppHost")

    val dcpInstancePrefix = generateDcpInstancePrefix()
    val browserToken = generateBrowserToken()

    private val _appHostConfig = MutableStateFlow<AspireHostModelConfig?>(null)
    private val _dashboardUrl: MutableStateFlow<String?> = MutableStateFlow(null)
    val dashboardUrl: StateFlow<String?> = _dashboardUrl.asStateFlow()

    private val _resources = ConcurrentHashMap<String, AspireResource>()
    private val _pendingChildren = ConcurrentHashMap<String, MutableList<Pair<String, AspireResource>>>()

    private val _rootResources = MutableStateFlow<List<AspireResource>>(emptyList())
    val rootResources: StateFlow<List<AspireResource>> = _rootResources.asStateFlow()

    val appHostState = project.messageBus.subscribeAsFlow(AppHostListener.TOPIC) {
        object : AppHostListener {
            override fun appHostStarted(
                appHostMainFilePath: Path,
                runConfigName: String?,
                processHandler: ProcessHandler
            ) {
                if (mainFilePath != appHostMainFilePath) return

                LOG.trace { "Aspire AppHost $mainFilePath was started" }
                val handler =
                    if (processHandler is DebuggerWorkerProcessHandler) processHandler.debuggerWorkerRealHandler
                    else processHandler
                val console = createConsole(
                    ConsoleKind.Normal,
                    handler,
                    project
                )
                Disposer.register(this@AspireAppHost, console)

                trySend(AspireAppHostState.Started(runConfigName, handler, console))
            }

            override fun appHostStopped(appHostMainFilePath: Path) {
                if (mainFilePath != appHostMainFilePath) return
                LOG.trace { "Aspire AppHost $mainFilePath was stopped" }

                trySend(AspireAppHostState.Stopped)
            }
        }
    }.stateIn(cs, SharingStarted.Eagerly, AspireAppHostState.Inactive)

    init {
        cs.launch {
            watchGrpcResources()
        }
    }

    fun subscribeToAspireAppHostModel(appHostModel: AspireHostModel, appHostLifetime: Lifetime) {
        LOG.trace("Subscribing to Aspire AppHost model")

        val appHostConfig = appHostModel.config
        appHostLifetime.bracketIfAlive({
            _appHostConfig.value = appHostConfig
            _dashboardUrl.value = appHostConfig.aspireHostProjectUrl
        }, {
            if (_appHostConfig.value?.id == appHostConfig.id) {
                _appHostConfig.value = null
            }
            _dashboardUrl.value = null
        })
        setOTLPEndpointUrl(appHostConfig, appHostLifetime)
    }

    private fun setOTLPEndpointUrl(appHostConfig: AspireHostModelConfig, appHostLifetime: Lifetime) {
        if (appHostConfig.otlpEndpointUrl == null) return

        val extension = OpenTelemetryProtocolServerExtension.getEnabledExtension() ?: return

        appHostLifetime.bracketIfAlive({
            extension.setOTLPServerEndpointForProxying(appHostConfig.otlpEndpointUrl)
        }, {
            extension.removeOTLPServerEndpointForProxying(appHostConfig.otlpEndpointUrl)
        })
    }

    private suspend fun watchGrpcResources() {
        combine(appHostState, _appHostConfig) { state, config -> state to config }
            .collectLatest { (state, config) ->
                clearResources()

                if (state !is AspireAppHostState.Started) return@collectLatest

                val appHostConfig = config ?: return@collectLatest
                val resourceServiceEndpointUrl = appHostConfig.resourceServiceEndpointUrl
                if (resourceServiceEndpointUrl.isNullOrBlank()) {
                    LOG.trace { "Aspire AppHost $mainFilePath does not provide a resource service endpoint" }
                    return@collectLatest
                }

                val client = try {
                    AspireDashboardClient.create(project, resourceServiceEndpointUrl, appHostConfig.resourceServiceApiKey)
                } catch (t: Throwable) {
                    LOG.warn("Unable to initialize Aspire dashboard gRPC client for $mainFilePath", t)
                    return@collectLatest
                }

                try {
                    watchResources(client)
                } finally {
                    client.dispose()
                    clearResources()
                }
            }
    }

    private suspend fun watchResources(client: AspireDashboardClient) {
        var isReconnect = false
        var retryDelayMs = INITIAL_RETRY_DELAY_MS

        while (currentCoroutineContext().isActive) {
            try {
                client.watchResources(isReconnect).collect { update ->
                    retryDelayMs = INITIAL_RETRY_DELAY_MS

                    when {
                        update.hasInitialData() ->
                            handleInitialResourceData(update.initialData, client)

                        update.hasChanges() ->
                            handleResourceChanges(update.changes, client)

                        else -> Unit
                    }
                }

                if (!currentCoroutineContext().isActive) break

                LOG.trace { "Aspire resource watch stream completed for $mainFilePath, reconnecting" }
                isReconnect = true
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                throw cancellation
            } catch (t: Throwable) {
                if (!currentCoroutineContext().isActive) break
                LOG.warn("Aspire resource watch failed for $mainFilePath, retrying", t)
            }

            delay(retryDelayMs)
            retryDelayMs = nextRetryDelay(retryDelayMs)
            isReconnect = true
        }
    }

    private fun handleInitialResourceData(
        initialData: InitialResourceData,
        client: AspireDashboardClient
    ) {
        clearResources()

        for (protoResource in initialData.resourcesList) {
            upsertGrpcResource(protoResource, client)
        }
    }

    private fun handleResourceChanges(
        changes: WatchResourcesChanges,
        client: AspireDashboardClient
    ) {
        for (change in changes.valueList) {
            when {
                change.hasUpsert() ->
                    upsertGrpcResource(change.upsert, client)

                change.hasDelete() ->
                    deleteGrpcResource(change.delete.resourceName)

                else -> Unit
            }
        }
    }

    private fun upsertGrpcResource(protoResource: ProtoResource, client: AspireDashboardClient) {
        val resourceName = protoResource.name
        val resourceModel = protoResource.toResourceModel()

        if (resourceModel.isHidden || resourceModel.state == ResourceState.Hidden) {
            deleteGrpcResource(resourceName)
            return
        }

        val parentResourceName = resourceModel.findParentResourceName()
        val existingResource = _resources[resourceName]
        if (existingResource == null) {
            LOG.trace { "Adding a new Aspire resource with id $resourceName to the AppHost $mainFilePath" }

            val resource = AspireResource(resourceName, resourceModel, protoResource.resourceType, client, cs, project)
            resource.updateCommandParameters(protoResource.commandsList)

            createResource(resourceName, resource, parentResourceName)

            project.messageBus.syncPublisher(ResourceListener.TOPIC).resourceCreated(resource)
            return
        }

        val previousParentResourceName = existingResource.resourceState.value.parentResourceName
        existingResource.update(resourceModel)
        existingResource.updateCommandParameters(protoResource.commandsList)

        if (previousParentResourceName != parentResourceName) {
            reparentResource(resourceName, existingResource, previousParentResourceName, parentResourceName)
        }
    }

    private fun createResource(resourceName: String, resource: AspireResource, parentResourceName: String?) {
        _resources[resourceName] = resource
        Disposer.register(this, resource)

        attachResource(resourceName, resource, parentResourceName)
        processPendingResources(resourceName, resource)
    }

    private fun attachResource(resourceName: String, resource: AspireResource, parentResourceName: String?) {
        val parentResource = parentResourceName?.let { _resources[it] }
        if (parentResourceName == null) {
            _rootResources.update { if (resource in it) it else it + resource }
        } else if (parentResource != null) {
            _rootResources.update { it - resource }
            parentResource.addChildResource(resource)
        } else {
            _rootResources.update { if (resource in it) it else it + resource }
            _pendingChildren
                .getOrPut(parentResourceName) { mutableListOf() }
                .also { pending -> pending.removeIf { it.first == resourceName } }
                .add(resourceName to resource)
        }
    }

    private fun processPendingResources(parentName: String, parentResource: AspireResource) {
        val pending = _pendingChildren.remove(parentName) ?: return

        for ((childName, childResource) in pending) {
            if (_resources[childName] != childResource) continue
            _rootResources.update { it - childResource }
            parentResource.addChildResource(childResource)
        }
    }

    private fun reparentResource(
        resourceName: String,
        resource: AspireResource,
        previousParentResourceName: String?,
        parentResourceName: String?
    ) {
        detachResource(resourceName, resource, previousParentResourceName)
        attachResource(resourceName, resource, parentResourceName)
    }

    private fun detachResource(resourceName: String, resource: AspireResource, parentResourceName: String?) {
        val parentResource = parentResourceName?.let { _resources[it] }
        if (parentResource == null) {
            _rootResources.update { it - resource }
        } else {
            parentResource.removeChildResource(resource)
        }

        if (parentResourceName != null) {
            _pendingChildren[parentResourceName]?.removeIf { it.first == resourceName }
        }
    }

    private fun deleteGrpcResource(resourceName: String) {
        val resource = _resources[resourceName] ?: return
        removeResource(resourceName, resource, resource.resourceState.value.parentResourceName)
    }

    private fun removeResource(resourceName: String, resource: AspireResource, parentResourceName: String?) {
        val removedResource = _resources.remove(resourceName)
        if (removedResource == null) return

        detachResource(resourceName, resource, parentResourceName)
        orphanChildren(resourceName, removedResource)

        project.messageBus.syncPublisher(ResourceListener.TOPIC).resourceDeleted(removedResource)
        Disposer.dispose(removedResource)
    }

    private fun orphanChildren(parentResourceName: String, parentResource: AspireResource) {
        for (childResource in parentResource.childrenResources.value.toList()) {
            parentResource.removeChildResource(childResource)
            attachResource(childResource.resourceState.value.name, childResource, parentResourceName)
        }
    }

    private fun clearResources() {
        val resources = _resources.values.toSet()
        _resources.clear()
        _pendingChildren.clear()
        _rootResources.value = emptyList()

        for (resource in resources) {
            project.messageBus.syncPublisher(ResourceListener.TOPIC).resourceDeleted(resource)
            Disposer.dispose(resource)
        }
    }

    private fun nextRetryDelay(currentDelayMs: Long): Long {
        return (currentDelayMs * 3 / 2).coerceAtMost(MAX_RETRY_DELAY_MS)
    }

    fun createSession(
        createSessionRequest: CreateSessionRequest,
        sessionEvents: Channel<SessionEvent>,
        lifetime: Lifetime
    ): CreateSessionResponse {
        val appHostStartedState = appHostState.value as? AspireAppHostState.Started

        val configuration = createSessionLaunchConfiguration(createSessionRequest)
        if (configuration == null) {
            LOG.warn("Unsupported session request type: ${createSessionRequest::class}")
            return CreateSessionResponse(null, ErrorCode.UnsupportedLaunchConfigurationType)
        }

        val sessionId = UUID.randomUUID().toString()

        LOG.trace { "Creating Aspire session with id: $sessionId" }

        val request = StartSessionRequest(
            sessionId,
            configuration,
            sessionEvents,
            appHostStartedState?.runConfigName,
            lifetime.createNested()
        )

        SessionManager.getInstance(project).submitRequest(request)

        return CreateSessionResponse(sessionId, null)
    }

    private fun createSessionLaunchConfiguration(createSessionRequest: CreateSessionRequest) =
        when (createSessionRequest) {
            is CreateProjectSessionRequest -> DotNetSessionLaunchConfiguration(
                Path(createSessionRequest.projectPath),
                createSessionRequest.debug,
                createSessionRequest.launchProfile,
                createSessionRequest.disableLaunchProfile,
                createSessionRequest.args?.toList(),
                createSessionRequest.envs?.map { it.key to it.value }
            )

            is CreatePythonSessionRequest -> PythonSessionLaunchConfiguration(
                Path(createSessionRequest.programPath),
                createSessionRequest.debug,
                createSessionRequest.interpreterPath,
                createSessionRequest.module,
                createSessionRequest.args?.toList(),
                createSessionRequest.envs?.map { it.key to it.value }
            )

            else -> null
        }

    fun deleteSession(deleteSessionRequest: DeleteSessionRequest): DeleteSessionResponse {
        LOG.trace { "Deleting Aspire session with id: ${deleteSessionRequest.sessionId}" }

        val request = StopSessionRequest(deleteSessionRequest.sessionId)

        SessionManager.getInstance(project).submitRequest(request)

        return DeleteSessionResponse(deleteSessionRequest.sessionId, null)
    }

    override fun dispose() {
        clearResources()
    }

    private fun generateDcpInstancePrefix(): String {
        val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        return (1..5)
            .map { allowedChars.random() }
            .joinToString("")
    }

    private fun generateBrowserToken(): String {
        return UUID.randomUUID().toString()
    }

    sealed interface AspireAppHostState {
        data object Inactive : AspireAppHostState

        data class Started(
            val runConfigName: String?,
            val processHandler: ProcessHandler,
            val console: ConsoleView
        ) : AspireAppHostState

        data object Stopped : AspireAppHostState
    }
}
