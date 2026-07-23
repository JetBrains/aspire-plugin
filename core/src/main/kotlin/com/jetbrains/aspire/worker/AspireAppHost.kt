@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.worker

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.messages.impl.subscribeAsFlow
import com.jetbrains.aspire.generated.*
import com.jetbrains.aspire.sessions.*
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rdclient.protocol.RdDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
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
 * @param mainFilePath path to the main project file (.csproj or .cs) of the AppHost
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

    private val sessionEventChannel = Channel<SessionEvent>(Channel.UNLIMITED)

    /** The AppHost-specific, single-consumer session-event stream used by the active DCP transport. */
    internal val sessionEvents: ReceiveChannel<SessionEvent>
        get() = sessionEventChannel
    private val sessionEventHandler = SessionEventHandler()

    val dcpInstancePrefix = generateDcpInstancePrefix()
    val browserToken = generateBrowserToken()

    private val resourceTreeManager = ResourceTreeManager(mainFilePath, project, cs, this)
    private val otlpProxyManager = AppHostOtlpProxyManager(cs)

    val rootResources: StateFlow<List<AspireResource>>
        get() = resourceTreeManager.rootResources

    private val appHostLifecycleEvents: SharedFlow<AppHostLifecycleEvent> =
        project.messageBus.subscribeAsFlow(AppHostListener.TOPIC) {
            object : AppHostListener {
                override fun appHostStarting(appHostFilePath: Path, environment: AppHostEnvironment) {
                    if (mainFilePath != appHostFilePath) return

                    LOG.trace { "Aspire AppHost $mainFilePath is starting" }
                    trySend(AppHostLifecycleEvent.Starting(environment))
                }

                override fun appHostStarted(
                    appHostFilePath: Path,
                    runConfigName: String?,
                    logFlow: SharedFlow<AppHostLogEntry>
                ) {
                    if (mainFilePath != appHostFilePath) return

                    LOG.trace { "Aspire AppHost $mainFilePath was started" }
                    trySend(AppHostLifecycleEvent.Started(runConfigName, logFlow))
                }

                override fun appHostStopped(appHostFilePath: Path) {
                    if (mainFilePath != appHostFilePath) return
                    LOG.trace { "Aspire AppHost $mainFilePath was stopped" }

                    trySend(AppHostLifecycleEvent.Stopped)
                }
            }
        }.shareIn(cs, SharingStarted.Eagerly)

    val currentLogFlow: StateFlow<SharedFlow<AppHostLogEntry>?> =
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

    init {
        otlpProxyManager.observeAppHostState(appHostState)
        resourceTreeManager.observeAppHostState(appHostState)
    }

    fun subscribeToAspireAppHostModel(
        appHostModel: AspireHostModel,
        dispatcher: RdDispatcher,
        appHostLifetime: Lifetime
    ) {
        appHostLifetime.coroutineScope.launch {
            for (event in sessionEventChannel) {
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
            sessionEventChannel,
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
        LOG.trace { "Disposing AspireAppHost for project: $mainFilePath" }
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
    ) {
        companion object {
            val EMPTY = AppHostEnvironment(null, null, null, null)
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
