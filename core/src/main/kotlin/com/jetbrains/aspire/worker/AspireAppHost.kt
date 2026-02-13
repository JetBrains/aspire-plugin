@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.worker

import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.messages.impl.subscribeAsFlow
import com.jetbrains.aspire.dashboard.AspireResource
import com.jetbrains.aspire.generated.*
import com.jetbrains.aspire.otlp.OpenTelemetryProtocolServerExtension
import com.jetbrains.aspire.sessions.*
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.debugger.DebuggerWorkerProcessHandler
import com.jetbrains.rider.run.ConsoleKind
import com.jetbrains.rider.run.createConsole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.util.*
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
class AspireAppHost(val mainFilePath: Path, private val project: Project, parentCs: CoroutineScope) : Disposable {
    companion object {
        private val LOG = logger<AspireAppHost>()
    }

    private val cs = parentCs.childScope("Aspire AppHost")

    private val _dashboardUrl: MutableStateFlow<String?> = MutableStateFlow(null)
    val dashboardUrl: StateFlow<String?> = _dashboardUrl.asStateFlow()

    private val _resources: MutableStateFlow<List<AspireResource>> = MutableStateFlow(emptyList())
    val resources: StateFlow<List<AspireResource>> = _resources.asStateFlow()

    val appHostState = project.messageBus.subscribeAsFlow(AppHostListener.TOPIC) {
        object : AppHostListener {
            override fun appHostStarted(appHostMainFilePath: Path, processHandler: ProcessHandler) {
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

                trySend(AspireAppHostState.Started(handler, console))
            }

            override fun appHostStopped(appHostMainFilePath: Path) {
                if (mainFilePath != appHostMainFilePath) return
                LOG.trace { "Aspire AppHost $mainFilePath was stopped" }

                _resources.value = emptyList()

                trySend(AspireAppHostState.Stopped)
            }
        }
    }.stateIn(cs, SharingStarted.Eagerly, AspireAppHostState.Inactive)

    fun subscribeToAspireAppHostModel(appHostModel: AspireHostModel, appHostLifetime: Lifetime) {
        LOG.trace("Subscribing to Aspire AppHost model")

        setAppHostUrl(appHostModel.config, appHostLifetime)
        setOTLPEndpointUrl(appHostModel.config, appHostLifetime)

        val sessionEvents = Channel<SessionEvent>(Channel.UNLIMITED)

        appHostModel.createSession.set { request ->
            createSession(request, sessionEvents, appHostModel.config, appHostLifetime)
        }

        appHostModel.deleteSession.set { request ->
            deleteSession(request)
        }

        appHostLifetime.coroutineScope.launch {
            for (event in sessionEvents) {
                handleSessionEvent(event, appHostModel)
            }
        }

        appHostModel.resources.view(appHostLifetime) { resourceLifetime, resourceId, resourceModel ->
            viewResource(resourceId, resourceModel, resourceLifetime)
        }
    }

    private fun setAppHostUrl(appHostConfig: AspireHostModelConfig, appHostLifetime: Lifetime) {
        appHostLifetime.bracketIfAlive({
            _dashboardUrl.value = appHostConfig.aspireHostProjectUrl
        }, {
            _dashboardUrl.value = null
        })
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

    private fun createSession(
        createSessionRequest: CreateSessionRequest,
        sessionEvents: Channel<SessionEvent>,
        appHostConfig: AspireHostModelConfig,
        appHostLifetime: Lifetime
    ): CreateSessionResponse {
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
            appHostConfig.runConfigName,
            appHostLifetime.createNested()
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

    private fun deleteSession(deleteSessionRequest: DeleteSessionRequest): DeleteSessionResponse {
        LOG.trace { "Deleting Aspire session with id: ${deleteSessionRequest.sessionId}" }

        val request = StopSessionRequest(deleteSessionRequest.sessionId)

        SessionManager.getInstance(project).submitRequest(request)

        return DeleteSessionResponse(deleteSessionRequest.sessionId, null)
    }

    private suspend fun handleSessionEvent(sessionEvent: SessionEvent, appHostModel: AspireHostModel) {
        when (sessionEvent) {
            is SessionProcessStarted -> handleProcessStartedEvent(sessionEvent, appHostModel)
            is SessionProcessTerminated -> handleProcessTerminatedEvent(sessionEvent, appHostModel)
            is SessionLogReceived -> handleSessionLogReceivedEvent(sessionEvent, appHostModel)
            is SessionMessageReceived -> handleSessionMessageReceivedEvent(sessionEvent, appHostModel)
        }
    }

    private suspend fun handleProcessStartedEvent(event: SessionProcessStarted, appHostModel: AspireHostModel) {
        LOG.trace { "Aspire session started (${event.id}, ${event.pid})" }
        withContext(Dispatchers.EDT) {
            appHostModel.processStarted.fire(ProcessStarted(event.id, event.pid))
        }
    }

    private suspend fun handleProcessTerminatedEvent(event: SessionProcessTerminated, appHostModel: AspireHostModel) {
        LOG.trace { "Aspire session terminated (${event.id}, ${event.exitCode})" }
        withContext(Dispatchers.EDT) {
            appHostModel.processTerminated.fire(ProcessTerminated(event.id, event.exitCode))
        }
    }

    private suspend fun handleSessionLogReceivedEvent(event: SessionLogReceived, appHostModel: AspireHostModel) {
        LOG.trace { "Aspire session log received (${event.id}, ${event.isStdErr}, ${event.message})" }
        withContext(Dispatchers.EDT) {
            appHostModel.logReceived.fire(
                LogReceived(
                    event.id,
                    event.isStdErr,
                    event.message
                )
            )
        }
    }

    private suspend fun handleSessionMessageReceivedEvent(
        event: SessionMessageReceived,
        appHostModel: AspireHostModel
    ) {
        LOG.trace { "Aspire session message received (${event.id}, ${event.level}, ${event.message})" }
        withContext(Dispatchers.EDT) {
            appHostModel.messageReceived.fire(
                MessageReceived(
                    event.id,
                    event.level,
                    event.message,
                    event.errorCode,
                )
            )
        }
    }

    private fun viewResource(
        resourceId: String,
        resourceModelWrapper: ResourceWrapper,
        resourceLifetime: Lifetime
    ) {
        LOG.trace { "Adding a new Aspire resource with id $resourceId to the AppHost $mainFilePath" }

        val resourceModel = resourceModelWrapper.model.valueOrNull ?: return
        if (resourceModel.isHidden || resourceModel.state == ResourceState.Hidden) {
            LOG.trace { "Aspire resource with id $resourceId is hidden" }
            return
        }

        val resource = AspireResource(
            resourceId,
            resourceModelWrapper,
            this,
            resourceLifetime,
            project
        )
        Disposer.register(this, resource)

        resourceLifetime.bracketIfAlive({
            _resources.update { currentList ->
                currentList + resource
            }
        }, {
            _resources.update { currentList ->
                currentList.filter { it.resourceId != resourceId }
            }
        })

        resourceModelWrapper.isInitialized.set(true)
    }

    fun getChildResourcesFor(resourceName: String) = buildList {
        for (resource in resources.value) {
            val parentResourceName = resource.parentResourceName
            if (parentResourceName == resourceName) add(resource)
        }
    }.sortedWith(compareBy({ it.data.type }, { it.data.name }))

    override fun dispose() {
    }

    sealed interface AspireAppHostState {
        data object Inactive : AspireAppHostState

        data class Started(val processHandler: ProcessHandler, val console: ConsoleView) : AspireAppHostState

        data object Stopped : AspireAppHostState
    }
}