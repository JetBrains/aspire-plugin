package com.jetbrains.aspire.worker

import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.dashboard.AspireProjectResourceProfileData
import com.jetbrains.aspire.dashboard.AspireResource
import com.jetbrains.aspire.generated.*
import com.jetbrains.aspire.otlp.OpenTelemetryProtocolServerExtension
import com.jetbrains.aspire.run.AspireRunConfiguration
import com.jetbrains.aspire.sessions.*
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.debugger.DebuggerWorkerProcessHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.Path

class AspireAppHost(val mainFilePath: Path, private val project: Project) : Disposable {
    companion object {
        private val LOG = logger<AspireAppHost>()
    }

    private val _dashboardUrl: MutableStateFlow<String?> = MutableStateFlow(null)
    val dashboardUrl: StateFlow<String?> = _dashboardUrl.asStateFlow()

    private val _appHostState: MutableStateFlow<AspireAppHostState> = MutableStateFlow(AspireAppHostState.Inactive)
    val appHostState: StateFlow<AspireAppHostState> = _appHostState.asStateFlow()

    private val _resources: MutableStateFlow<List<AspireResource>> = MutableStateFlow(emptyList())
    val resources: StateFlow<List<AspireResource>> = _resources.asStateFlow()

    private val _resourcesReloadSignal = MutableSharedFlow<Unit>(1)
    val resourcesReloadSignal: SharedFlow<Unit> = _resourcesReloadSignal.asSharedFlow()

    private val resourceProfileData = ConcurrentHashMap<String, AspireProjectResourceProfileData>()

    init {
        project.messageBus.connect(this).subscribe(ExecutionManager.EXECUTION_TOPIC, object : ExecutionListener {
            override fun processStarted(
                executorId: String,
                env: ExecutionEnvironment,
                handler: ProcessHandler
            ) {
                val profile = env.runProfile
                if (profile is AspireRunConfiguration) {
                    val projectFilePath = Path(profile.parameters.mainFilePath)
                    if (mainFilePath != projectFilePath) return
                    appHostStarted(handler)
                } else if (profile is SessionProfile) {
                    val appHostMainFilePath = profile.aspireHostProjectPath ?: return
                    if (mainFilePath != appHostMainFilePath) return
                    setSessionProfile(profile)
                }
            }

            override fun processTerminated(
                executorId: String,
                env: ExecutionEnvironment,
                handler: ProcessHandler,
                exitCode: Int
            ) {
                val profile = env.runProfile
                if (profile is AspireRunConfiguration) {
                    val projectFilePath = Path(profile.parameters.mainFilePath)
                    if (mainFilePath != projectFilePath) return
                    appHostStopped()
                } else if (profile is SessionProfile) {
                    val appHostMainFilePath = profile.aspireHostProjectPath ?: return
                    if (mainFilePath != appHostMainFilePath) return
                    removeSessionProfile(profile)
                }
            }
        })
    }

    private fun appHostStarted(processHandler: ProcessHandler) {
        LOG.trace { "Aspire AppHost $mainFilePath was started" }

        val handler =
            if (processHandler is DebuggerWorkerProcessHandler) processHandler.debuggerWorkerRealHandler
            else processHandler

        _appHostState.value = AspireAppHostState.Started(handler)
    }

    private fun appHostStopped() {
        LOG.trace { "Aspire AppHost $mainFilePath was stopped" }

        _appHostState.value = AspireAppHostState.Stopped
        _resources.value = emptyList()
        resourceProfileData.clear()
    }

    private fun setSessionProfile(profile: SessionProfile) {
        val profileData = AspireProjectResourceProfileData(profile.projectPath, profile.isDebugMode)

        resourceProfileData[profile.sessionId] = profileData

        resources.value
            .filter { it.projectPath?.value == profileData.projectPath }
            .forEach { it.setProfileData(profileData) }
    }

    private fun removeSessionProfile(profile: SessionProfile) {
        resourceProfileData.remove(profile.sessionId)
    }

    private fun setProfileDataForResource(resource: AspireResource) {
        val projectPath = resource.projectPath ?: return
        val profileData = resourceProfileData.values.firstOrNull { it.projectPath == projectPath.value } ?: return
        resource.setProfileData(profileData)
    }

    fun subscribeToAspireAppHostModel(appHostModel: AspireHostModel, appHostLifetime: Lifetime) {
        LOG.trace(" Subscribing to Aspire AppHost model")

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

    fun childResourceTypeChanged() {
        _resourcesReloadSignal.tryEmit(Unit)
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

        val extension = OpenTelemetryProtocolServerExtension.EP_NAME.extensionList.singleOrNull { it.enabled } ?: return
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
        val sessionId = UUID.randomUUID().toString()

        LOG.trace { "Creating session with id: $sessionId" }

        val launchConfiguration = DotNetSessionLaunchConfiguration(
            Path(createSessionRequest.projectPath),
            createSessionRequest.debug,
            createSessionRequest.launchProfile,
            createSessionRequest.disableLaunchProfile,
            createSessionRequest.args?.toList(),
            createSessionRequest.envs?.map { it.key to it.value }
        )

        val request = StartSessionRequest(
            sessionId,
            launchConfiguration,
            sessionEvents,
            appHostConfig.runConfigName,
            appHostLifetime.createNested()
        )

        SessionManager.getInstance(project).submitRequest(request)

        return CreateSessionResponse(sessionId, null)
    }

    private fun deleteSession(deleteSessionRequest: DeleteSessionRequest): DeleteSessionResponse {
        LOG.trace { "Deleting session with id: ${deleteSessionRequest.sessionId}" }

        val request = StopSessionRequest(deleteSessionRequest.sessionId)

        SessionManager.getInstance(project).submitRequest(request)

        return DeleteSessionResponse(deleteSessionRequest.sessionId, null)
    }

    private suspend fun handleSessionEvent(sessionEvent: SessionEvent, appHostModel: AspireHostModel) {
        when (sessionEvent) {
            is SessionStarted -> {
                LOG.trace { "Aspire session started (${sessionEvent.id}, ${sessionEvent.pid})" }
                withContext(Dispatchers.EDT) {
                    appHostModel.processStarted.fire(ProcessStarted(sessionEvent.id, sessionEvent.pid))
                }
            }

            is SessionTerminated -> {
                LOG.trace { "Aspire session terminated (${sessionEvent.id}, ${sessionEvent.exitCode})" }
                withContext(Dispatchers.EDT) {
                    appHostModel.processTerminated.fire(ProcessTerminated(sessionEvent.id, sessionEvent.exitCode))
                }
            }

            is SessionLogReceived -> {
                LOG.trace { "Aspire session log received (${sessionEvent.id}, ${sessionEvent.isStdErr}, ${sessionEvent.message})" }
                withContext(Dispatchers.EDT) {
                    appHostModel.logReceived.fire(
                        LogReceived(
                            sessionEvent.id,
                            sessionEvent.isStdErr,
                            sessionEvent.message
                        )
                    )
                }
            }
        }
    }

    private fun viewResource(
        resourceId: String,
        resourceModelWrapper: ResourceWrapper,
        resourceLifetime: Lifetime
    ) {
        LOG.trace { "Adding a new resource with id $resourceId to the AppHost $mainFilePath" }

        val resourceModel = resourceModelWrapper.model.valueOrNull ?: return
        if (resourceModel.isHidden || resourceModel.state == ResourceState.Hidden) {
            LOG.trace { "Resource with id $resourceId is hidden" }
            return
        }

        val resource = AspireResource(resourceId, resourceModelWrapper, this, resourceLifetime, project)

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

        setProfileDataForResource(resource)
    }

    override fun dispose() {
    }

    fun getChildResourcesFor(resourceName: String) = buildList {
        for (resource in resources.value) {
            val parentResourceName = resource.parentResourceName
            if (parentResourceName == resourceName) add(resource)
        }
    }.sortedWith(compareBy({ it.type }, { it.name }))

    fun getProjectResource(projectPath: Path): AspireResource? {
        for (resource in resources.value) {
            if (resource.type != ResourceType.Project) continue
            if (resource.projectPath?.value == projectPath) return resource
        }

        return null
    }

    sealed interface AspireAppHostState {
        data object Inactive : AspireAppHostState

        data class Started(val processHandler: ProcessHandler) : AspireAppHostState

        data object Stopped : AspireAppHostState
    }
}