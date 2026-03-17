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
import com.intellij.platform.util.coroutines.flow.zipWithNext
import com.intellij.util.messages.impl.subscribeAsFlow
import com.jetbrains.aspire.generated.*
import com.jetbrains.aspire.otlp.OpenTelemetryProtocolServerExtension
import com.jetbrains.aspire.sessions.*
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rdclient.protocol.RdDispatcher
import com.jetbrains.rider.debugger.DebuggerWorkerProcessHandler
import com.jetbrains.rider.run.ConsoleKind
import com.jetbrains.rider.run.createConsole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
 * - Tracking resources running within this AppHost (delegated to [ResourceTreeManager])
 * - Managing the dashboard URL and OTLP endpoint configuration
 * - Forwarding session events (started, terminated, log received) back to the AppHost model
 *   (delegated to [SessionEventHandler])
 *
 * @param mainFilePath path to the main project file (.csproj or .cs) of the AppHost
 *
 * @see AspireWorker for the service that manages this object
 * @see SessionManager for session request processing
 * @see ResourceTreeManager for resource tree management and dashboard client lifecycle
 * @see SessionEventHandler for session event dispatching
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
    }

    private val cs = parentCs.childScope("Aspire AppHost")

    private val sessionEvents = Channel<SessionEvent>(Channel.UNLIMITED)
    private val sessionEventHandler = SessionEventHandler()

    val dcpInstancePrefix = generateDcpInstancePrefix()
    val browserToken = generateBrowserToken()

    private val resourceTreeManager = ResourceTreeManager(mainFilePath, project, cs, this)

    val rootResources: StateFlow<List<AspireResource>>
        get() = resourceTreeManager.rootResources

    private val appHostLifecycleEvents: Flow<AppHostLifecycleEvent> =
        project.messageBus.subscribeAsFlow(AppHostListener.TOPIC) {
            object : AppHostListener {
                override fun appHostStarting(appHostMainFilePath: Path, environment: AppHostEnvironment) {
                    if (mainFilePath != appHostMainFilePath) return

                    LOG.trace { "Aspire AppHost $mainFilePath is starting" }
                    trySend(AppHostLifecycleEvent.Starting(environment))
                }

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

                    trySend(
                        AppHostLifecycleEvent.Started(
                            runConfigName,
                            handler,
                            console
                        )
                    )
                }

                override fun appHostStopped(appHostMainFilePath: Path) {
                    if (mainFilePath != appHostMainFilePath) return
                    LOG.trace { "Aspire AppHost $mainFilePath was stopped" }

                    trySend(AppHostLifecycleEvent.Stopped)
                }
            }
        }

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
                        event.processHandler,
                        event.console,
                        environment ?: AppHostEnvironment(null, null, null, null)
                    )
                }

                AppHostLifecycleEvent.Stopped -> AspireAppHostState.Stopped
            }
        }.stateIn(cs, SharingStarted.Eagerly, AspireAppHostState.Inactive)

    val dashboardUrl: StateFlow<String?> = appHostState.map {
        when (it) {
            AspireAppHostState.Inactive -> null
            is AspireAppHostState.Started -> it.environment.aspireHostProjectUrl
            is AspireAppHostState.Starting -> it.environment.aspireHostProjectUrl
            AspireAppHostState.Stopped -> name
        }
    }.stateIn(cs, SharingStarted.Eagerly, null)

    init {
        cs.launch {
            appHostState
                .zipWithNext()
                .collect { (previousState, currentState) ->
                    if (currentState is AspireAppHostState.Starting) {
                        val otlpEndpoint = currentState.environment.otlpEndpointUrl ?: return@collect
                        val extension = OpenTelemetryProtocolServerExtension.getEnabledExtension() ?: return@collect
                        extension.setOTLPServerEndpointForProxying(otlpEndpoint)
                    } else if (previousState is AspireAppHostState.Started && currentState is AspireAppHostState.Stopped) {
                        val otlpEndpoint = previousState.environment.otlpEndpointUrl ?: return@collect
                        val extension = OpenTelemetryProtocolServerExtension.getEnabledExtension() ?: return@collect
                        extension.removeOTLPServerEndpointForProxying(otlpEndpoint)
                    }
                }
        }

        cs.launch {
            var dashboardJob: Job? = null
            appHostState
                .zipWithNext()
                .collect { (previousState, currentState) ->
                    if (currentState is AspireAppHostState.Started) {
                        dashboardJob = with(resourceTreeManager) {
                            this@launch.startDashboardClient(currentState.environment)
                        }
                    } else if (previousState is AspireAppHostState.Started && currentState is AspireAppHostState.Stopped) {
                        dashboardJob?.cancel()
                        dashboardJob = null
                    }
                }
        }
    }

    fun subscribeToAspireAppHostModel(appHostModel: AspireHostModel, dispatcher: RdDispatcher, appHostLifetime: Lifetime) {
        appHostLifetime.coroutineScope.launch {
            for (event in sessionEvents) {
                sessionEventHandler.handleSessionEvent(event, appHostModel, dispatcher)
            }
        }
    }

    fun createSession(
        createSessionRequest: CreateSessionRequest,
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
        cs.cancel()
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

    data class AppHostEnvironment(
        val resourceServiceEndpointUrl: String?,
        val resourceServiceApiKey: String?,
        val otlpEndpointUrl: String?,
        val aspireHostProjectUrl: String?
    )

    private sealed interface AppHostLifecycleEvent {
        data class Starting(val environment: AppHostEnvironment) : AppHostLifecycleEvent

        data class Started(
            val runConfigName: String?,
            val processHandler: ProcessHandler,
            val console: ConsoleView,
        ) : AppHostLifecycleEvent

        data object Stopped : AppHostLifecycleEvent
    }

    sealed interface AspireAppHostState {
        data object Inactive : AspireAppHostState

        data class Starting(val environment: AppHostEnvironment) : AspireAppHostState

        data class Started(
            val runConfigName: String?,
            val processHandler: ProcessHandler,
            val console: ConsoleView,
            val environment: AppHostEnvironment,
        ) : AspireAppHostState

        data object Stopped : AspireAppHostState
    }
}
