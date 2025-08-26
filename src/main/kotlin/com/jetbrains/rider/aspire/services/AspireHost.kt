package com.jetbrains.rider.aspire.services

import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.services.ServiceEventListener
import com.intellij.execution.services.ServiceViewManager
import com.intellij.execution.services.ServiceViewProvidingContributor
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.application
import com.jetbrains.rd.framework.util.setSuspend
import com.jetbrains.rd.util.addUnique
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.threading.coroutines.lifetimedCoroutineScope
import com.jetbrains.rider.aspire.generated.*
import com.jetbrains.rider.aspire.otlp.OpenTelemetryProtocolServerExtension
import com.jetbrains.rider.aspire.run.AspireHostConfiguration
import com.jetbrains.rider.aspire.sessions.*
import com.jetbrains.rider.aspire.sessions.SessionManager.CreateSessionCommand
import com.jetbrains.rider.aspire.sessions.SessionManager.DeleteSessionCommand
import com.jetbrains.rider.aspire.sessions.projectLaunchers.ProjectSessionProfile
import com.jetbrains.rider.debugger.DebuggerWorkerProcessHandler
import com.jetbrains.rider.run.ConsoleKind
import com.jetbrains.rider.run.createConsole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.nameWithoutExtension

class AspireHost(
    val hostProjectPath: Path,
    private val scope: CoroutineScope,
    private val project: Project
) : ServiceViewProvidingContributor<AspireResource, AspireHost>, Disposable {
    companion object {
        private val LOG = logger<AspireHost>()
    }

    private val descriptor by lazy { AspireHostServiceViewDescriptor(this) }

    private val resources = ConcurrentHashMap<String, AspireResource>()
    private val resourceProfileData = ConcurrentHashMap<String, AspireProjectResourceProfileData>()

    val hostProjectPathString = hostProjectPath.absolutePathString()

    var displayName: String = hostProjectPath.nameWithoutExtension
        private set
    var isActive: Boolean = false
        private set
    var dashboardUrl: String? = null
        private set
    var consoleView: ConsoleView? = null
        private set

    init {
        project.messageBus.connect(this).subscribe(ExecutionManager.EXECUTION_TOPIC, object : ExecutionListener {
            override fun processStarted(
                executorId: String,
                env: ExecutionEnvironment,
                handler: ProcessHandler
            ) {
                val profile = env.runProfile
                if (profile is AspireHostConfiguration) {
                    val projectFilePath = Path(profile.parameters.projectFilePath)
                    if (hostProjectPath != projectFilePath) return

                    hostStarted(handler)
                } else if (profile is ProjectSessionProfile) {
                    val aspireHostProjectPath = profile.aspireHostProjectPath ?: return
                    if (hostProjectPath != aspireHostProjectPath) return

                    setProfileDataForResource(profile)
                }
            }

            override fun processTerminated(
                executorId: String,
                env: ExecutionEnvironment,
                handler: ProcessHandler,
                exitCode: Int
            ) {
                val profile = env.runProfile
                if (profile is AspireHostConfiguration) {
                    val projectFilePath = Path(profile.parameters.projectFilePath)
                    if (hostProjectPath != projectFilePath) return

                    hostStopped()
                } else if (profile is ProjectSessionProfile) {
                    val aspireHostProjectPath = profile.aspireHostProjectPath ?: return
                    if (hostProjectPath != aspireHostProjectPath) return

                    removeProfileData(profile)
                }
            }
        })
    }

    override fun asService() = this

    override fun getViewDescriptor(project: Project) = descriptor

    override fun getServices(project: Project) = getParentResources()

    override fun getServiceDescriptor(project: Project, aspireResource: AspireResource) =
        aspireResource.getViewDescriptor(project)

    fun getResources() = buildList {
        for (resource in resources.values) {
            if (resource.isHidden || resource.state == ResourceState.Hidden) continue
            add(resource)
        }
    }.sortedWith(compareBy({ it.type }, { it.name }))

    fun getParentResources() = buildList {
        for (resource in resources.values) {
            if (resource.isHidden || resource.state == ResourceState.Hidden) continue
            val parentResourceName = resource.parentResourceName
            if (parentResourceName == null) add(resource)
        }
    }.sortedWith(compareBy({ it.type }, { it.name }))

    fun getChildResourcesFor(resourceName: String) = buildList {
        for (resource in resources.values) {
            if (resource.isHidden || resource.state == ResourceState.Hidden) continue
            val parentResourceName = resource.parentResourceName
            if (parentResourceName == resourceName) add(resource)
        }
    }.sortedWith(compareBy({ it.type }, { it.name }))

    fun getProjectResource(projectPath: Path): AspireResource? {
        for (resource in resources) {
            if (resource.value.type != ResourceType.Project || resource.value.isHidden || resource.value.state == ResourceState.Hidden)
                continue

            if (resource.value.projectPath?.value == projectPath) return resource.value
        }

        return null
    }

    fun setAspireHostModel(model: AspireHostModel, aspireHostLifetime: Lifetime) {
        LOG.trace(" Subscribing to Aspire host model")

        setAspireHostUrl(model.config)
        setOTLPEndpointUrl(model.config, aspireHostLifetime)

        val sessionEvents = Channel<SessionEvent>(Channel.UNLIMITED)

        scope.launch(Dispatchers.EDT) {
            model.createSession.setSuspend { _, request ->
                createSession(request, sessionEvents, model.config, aspireHostLifetime)
            }

            model.deleteSession.setSuspend { _, request ->
                deleteSession(request)
            }

            lifetimedCoroutineScope(aspireHostLifetime) {
                for (event in sessionEvents) {
                    handleSessionEvent(event, model)
                }
            }
        }

        model.resources.view(aspireHostLifetime) { resourceLifetime, resourceId, resourceModel ->
            viewResource(resourceId, resourceModel, resourceLifetime)
        }
    }

    private suspend fun createSession(
        createSessionRequest: CreateSessionRequest,
        sessionEvents: Channel<SessionEvent>,
        aspireHostConfig: AspireHostModelConfig,
        aspireHostLifetime: Lifetime
    ): CreateSessionResponse {
        val sessionId = UUID.randomUUID().toString()

        LOG.trace { "Creating session with id: $sessionId" }

        val command = CreateSessionCommand(
            sessionId,
            createSessionRequest,
            sessionEvents,
            aspireHostConfig.isDebuggingMode,
            aspireHostConfig.runConfigName,
            aspireHostLifetime
        )

        SessionManager.getInstance(project).submitCommand(command)

        return CreateSessionResponse(sessionId, null)
    }

    private suspend fun deleteSession(deleteSessionRequest: DeleteSessionRequest): DeleteSessionResponse {
        LOG.trace { "Deleting session with id: ${deleteSessionRequest.sessionId}" }

        val command = DeleteSessionCommand(deleteSessionRequest.sessionId)

        SessionManager.getInstance(project).submitCommand(command)

        return DeleteSessionResponse(deleteSessionRequest.sessionId, null)
    }

    private fun handleSessionEvent(sessionEvent: SessionEvent, model: AspireHostModel) {
        when (sessionEvent) {
            is SessionStarted -> {
                LOG.trace { "Aspire session started (${sessionEvent.id}, ${sessionEvent.pid})" }
                model.processStarted.fire(ProcessStarted(sessionEvent.id, sessionEvent.pid))
            }

            is SessionTerminated -> {
                LOG.trace { "Aspire session terminated (${sessionEvent.id}, ${sessionEvent.exitCode})" }
                model.processTerminated.fire(ProcessTerminated(sessionEvent.id, sessionEvent.exitCode))
            }

            is SessionLogReceived -> {
                LOG.trace { "Aspire session log received (${sessionEvent.id}, ${sessionEvent.isStdErr}, ${sessionEvent.message})" }
                model.logReceived.fire(LogReceived(sessionEvent.id, sessionEvent.isStdErr, sessionEvent.message))
            }
        }
    }

    private fun viewResource(
        resourceId: String,
        resourceModel: ResourceWrapper,
        resourceLifetime: Lifetime
    ) {
        LOG.trace { "Adding a new resource with id $resourceId to the host $hostProjectPathString" }

        val resource = AspireResource(resourceModel, this, resourceLifetime, project)
        Disposer.register(this, resource)
        resources.addUnique(resourceLifetime, resourceId, resource)

        if (!resource.isHidden && resource.state != ResourceState.Hidden) {
            resourceLifetime.bracketIfAlive({
                sendServiceChildrenChangedEvent()
            }, {
                sendServiceChildrenChangedEvent()
            })
        }

        resourceModel.isInitialized.set(true)

        expand()

        setProfileDataForResource(resource)
    }

    private fun setAspireHostUrl(config: AspireHostModelConfig) {
        dashboardUrl = config.aspireHostProjectUrl

        sendServiceChangedEvent()
    }

    private fun setOTLPEndpointUrl(config: AspireHostModelConfig, lifetime: Lifetime) {
        if (config.otlpEndpointUrl == null) return

        val extension = OpenTelemetryProtocolServerExtension.EP_NAME.extensionList.singleOrNull { it.enabled } ?: return
        lifetime.bracketIfAlive({
            extension.setOTLPServerEndpointForProxying(config.otlpEndpointUrl)
        }, {
            extension.removeOTLPServerEndpointForProxying(config.otlpEndpointUrl)
        })
    }

    private fun hostStarted(processHandler: ProcessHandler) {
        LOG.trace { "Aspire Host $hostProjectPathString was started" }

        isActive = true

        val handler =
            if (processHandler is DebuggerWorkerProcessHandler) processHandler.debuggerWorkerRealHandler
            else processHandler
        val console = createConsole(
            ConsoleKind.Normal,
            handler,
            project
        )
        Disposer.register(this, console)
        consoleView = console

        selectHost()

        sendServiceChangedEvent()
    }

    private fun hostStopped() {
        LOG.trace { "Aspire Host $hostProjectPathString was stopped" }

        isActive = false
        dashboardUrl = null

        resources.clear()
        resourceProfileData.clear()

        sendServiceChangedEvent()
    }

    private fun setProfileDataForResource(resource: AspireResource) {
        val projectPath = resource.projectPath ?: return
        val profileData = resourceProfileData.values.firstOrNull { it.projectPath == projectPath.value } ?: return
        resource.setProfileData(profileData)
    }

    private fun setProfileDataForResource(profile: ProjectSessionProfile) {
        val profileData = AspireProjectResourceProfileData(profile.projectPath, profile.isDebugMode)

        resourceProfileData[profile.sessionId] = profileData

        resources.values
            .filter { it.projectPath?.value == profileData.projectPath }
            .forEach { it.setProfileData(profileData) }
    }

    private fun removeProfileData(profile: ProjectSessionProfile) {
        resourceProfileData.remove(profile.sessionId)
    }

    private fun selectHost() {
        application.invokeLater {
            ServiceViewManager
                .getInstance(project)
                .select(this, AspireMainServiceViewContributor::class.java, true, true)
        }
    }

    private fun expand() {
        application.invokeLater {
            ServiceViewManager
                .getInstance(project)
                .expand(this, AspireMainServiceViewContributor::class.java)
        }
    }

    private fun sendServiceChangedEvent() {
        val event = ServiceEventListener.ServiceEvent.createEvent(
            ServiceEventListener.EventType.SERVICE_CHANGED,
            this,
            AspireMainServiceViewContributor::class.java
        )
        project.messageBus.syncPublisher(ServiceEventListener.TOPIC).handle(event)
    }

    private fun sendServiceChildrenChangedEvent() {
        val event = ServiceEventListener.ServiceEvent.createEvent(
            ServiceEventListener.EventType.SERVICE_CHILDREN_CHANGED,
            this,
            AspireMainServiceViewContributor::class.java
        )
        project.messageBus.syncPublisher(ServiceEventListener.TOPIC).handle(event)
    }

    override fun dispose() {
    }
}