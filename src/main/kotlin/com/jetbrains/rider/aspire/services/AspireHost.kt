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
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.application
import com.jetbrains.rd.util.addUnique
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.aspire.generated.AspireHostModel
import com.jetbrains.rider.aspire.generated.ResourceState
import com.jetbrains.rider.aspire.generated.ResourceType
import com.jetbrains.rider.aspire.generated.ResourceWrapper
import com.jetbrains.rider.aspire.listeners.AspireSessionHostListener
import com.jetbrains.rider.aspire.run.AspireHostConfig
import com.jetbrains.rider.aspire.run.AspireHostConfiguration
import com.jetbrains.rider.aspire.sessionHost.projectLaunchers.ProjectSessionProfile
import com.jetbrains.rider.aspire.util.getServiceInstanceId
import com.jetbrains.rider.debugger.DebuggerWorkerProcessHandler
import com.jetbrains.rider.run.ConsoleKind
import com.jetbrains.rider.run.createConsole
import com.jetbrains.rider.runtime.DotNetExecutable
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.nameWithoutExtension

class AspireHost(
    val hostProjectPath: Path,
    private val project: Project
) : ServiceViewProvidingContributor<AspireResource, AspireHost>, Disposable {
    companion object {
        private val LOG = logger<AspireHost>()
    }

    private val resources = ConcurrentHashMap<String, AspireResource>()
    private val handlers = mutableMapOf<String, ProcessHandler>()
    private val lock = Any()

    val hostProjectPathString = hostProjectPath.absolutePathString()

    private val descriptor by lazy { AspireHostServiceViewDescriptor(this) }

    private val serviceEventPublisher = project.messageBus.syncPublisher(ServiceEventListener.TOPIC)

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

                    setHandlerForHost(profile.dotnetExecutable, handler)
                }
            }

            override fun processTerminated(
                executorId: String,
                env: ExecutionEnvironment,
                handler: ProcessHandler,
                exitCode: Int
            ) {
                val profile = env.runProfile
                if (profile !is AspireHostConfiguration) return

                val projectFilePath = Path(profile.parameters.projectFilePath)
                if (hostProjectPath != projectFilePath) return

                hostStopped()
            }
        })

        project.messageBus.connect(this).subscribe(AspireSessionHostListener.TOPIC, object : AspireSessionHostListener {
            override fun configCreated(
                aspireHostProjectPath: Path,
                config: AspireHostConfig
            ) {
                if (aspireHostProjectPath != hostProjectPath) return

                addAspireHostUrl(config)
            }
        })
    }

    override fun asService() = this

    override fun getViewDescriptor(project: Project) = descriptor

    override fun getServices(project: Project) = buildList {
        for (resource in resources) {
            if (resource.value.type == ResourceType.Unknown || resource.value.state == ResourceState.Hidden)
                continue
            add(resource.value)
        }
    }.sortedBy { it.type }

    override fun getServiceDescriptor(
        project: Project,
        aspireResource: AspireResource
    ) = aspireResource.getViewDescriptor()

    fun getResource(projectPath: Path): AspireResource? {
        for (resource in resources) {
            if (resource.value.type == ResourceType.Unknown || resource.value.state == ResourceState.Hidden)
                continue

            if (resource.value.projectPath == projectPath) return resource.value
        }

        return null
    }

    fun setAspireHostModel(model: AspireHostModel, lifetime: Lifetime) {
        model.resources.view(lifetime) { resourceLifetime, resourceId, resourceModel ->
            viewResource(resourceId, resourceModel, resourceLifetime)
        }
    }

    private fun viewResource(
        resourceId: String,
        resourceModel: ResourceWrapper,
        resourceLifetime: Lifetime
    ) {
        LOG.trace { "Adding a new resource with id $resourceId to the host $hostProjectPathString" }

        val resource = AspireResource(resourceModel, resourceLifetime, project)
        Disposer.register(this, resource)
        resources.addUnique(resourceLifetime, resourceId, resource)

        resourceModel.isInitialized.set(true)

        expand()

        val handler = synchronized(lock) {
            handlers.remove(resource.serviceInstanceId)
        }
        handler?.let { resource.setHandler(it) }
    }

    private fun addAspireHostUrl(config: AspireHostConfig) {
        dashboardUrl = config.aspireHostProjectUrl

        sendServiceChangedEvent()
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

        sendServiceChangedEvent()
    }

    private fun setHandlerForHost(dotnetExecutable: DotNetExecutable, processHandler: ProcessHandler) {
        val serviceInstanceId = dotnetExecutable.getServiceInstanceId() ?: return
        val resource = synchronized(lock) {
            val resource = resources.entries.firstOrNull { it.value.serviceInstanceId == serviceInstanceId }?.value
            if (resource == null) {
                handlers[serviceInstanceId] = processHandler
                return
            }
            resource
        }

        resource.setHandler(processHandler)
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
        serviceEventPublisher.handle(event)
    }

    override fun dispose() {
    }
}