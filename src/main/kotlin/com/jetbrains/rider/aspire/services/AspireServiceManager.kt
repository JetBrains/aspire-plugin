package com.jetbrains.rider.aspire.services

import com.intellij.execution.ExecutionResult
import com.intellij.execution.RunManagerListener
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.services.ServiceEventListener
import com.intellij.execution.services.ServiceViewManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.application
import com.jetbrains.rd.util.addUnique
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.aspire.generated.AspireSessionHostModel
import com.jetbrains.rider.aspire.generated.ResourceState
import com.jetbrains.rider.aspire.generated.ResourceType
import com.jetbrains.rider.aspire.generated.ResourceWrapper
import com.jetbrains.rider.aspire.run.AspireHostConfig
import com.jetbrains.rider.aspire.run.AspireHostConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.nameWithoutExtension

@Service(Service.Level.PROJECT)
class AspireServiceManager(private val project: Project) {
    companion object {
        fun getInstance(project: Project) = project.service<AspireServiceManager>()

        private val LOG = logger<AspireServiceManager>()
    }

    private val hostServices = ConcurrentHashMap<String, AspireHostService>()
    private val resourceServices = ConcurrentHashMap<String, MutableMap<String, AspireResourceService>>()

    fun getHostServices() = hostServices.values.toList()
    fun getHostService(hostPath: String) = hostServices[hostPath]
    fun getResourceServices(hostPath: String) =
        resourceServices[hostPath]?.values
            ?.asSequence()
            ?.filter { it.type != ResourceType.Unknown }
            ?.filter { it.state != ResourceState.Hidden }
            ?.sortedBy { it.type }
            ?.toList()
            ?: emptyList()

    private val serviceEventPublisher = project.messageBus.syncPublisher(ServiceEventListener.TOPIC)

    fun addAspireHostService(host: AspireHostService) {
        if (hostServices.containsKey(host.projectPathString)) return

        LOG.trace("Adding a new Aspire host ${host.projectPathString}")
        hostServices[host.projectPathString] = host
        resourceServices[host.projectPathString] = mutableMapOf()

        val event = ServiceEventListener.ServiceEvent.createEvent(
            ServiceEventListener.EventType.SERVICE_ADDED,
            host,
            AspireServiceContributor::class.java
        )
        serviceEventPublisher.handle(event)
    }

    fun removeAspireHostService(hostPath: Path) {
        val hostPathString = hostPath.absolutePathString()
        LOG.trace("Removing the Aspire host $hostPathString")

        val host = hostServices.remove(hostPathString)
        resourceServices.remove(hostPathString)
        if (host == null) return

        val event = ServiceEventListener.ServiceEvent.createEvent(
            ServiceEventListener.EventType.SERVICE_REMOVED,
            host,
            AspireServiceContributor::class.java
        )
        serviceEventPublisher.handle(event)
    }

    fun updateAspireHostService(hostPath: Path, name: String) {
        val hostPathString = hostPath.absolutePathString()
        LOG.trace("Updating the Aspire host $hostPathString")

        val host = hostServices[hostPathString] ?: return
        host.update(name)

        sendServiceChangedEvent(host)
    }

    fun updateAspireHostService(hostPath: Path, executionResult: ExecutionResult) {
        val hostPathString = hostPath.absolutePathString()
        LOG.trace("Setting the execution result to the Aspire host $hostPathString")

        val host = hostServices[hostPathString] ?: return
        host.update(executionResult, project)

        sendServiceChangedEvent(host)
    }

    suspend fun startAspireHostService(
        aspireHostConfig: AspireHostConfig,
        sessionHostModel: AspireSessionHostModel
    ) {
        val hostPathString = aspireHostConfig.aspireHostProjectPath.absolutePathString()
        LOG.trace("Starting the Aspire Host $hostPathString")

        val aspireHostServiceLifetime = aspireHostConfig.aspireHostLifetime.createNested()

        val hostService = hostServices[hostPathString] ?: return

        val serviceViewManager = ServiceViewManager.getInstance(project)
        withContext(Dispatchers.EDT) {
            serviceViewManager.select(hostService, AspireServiceContributor::class.java, true, true)
        }

        aspireHostServiceLifetime.bracketIfAlive({
            hostService.startHost(
                aspireHostConfig.aspireHostProjectUrl,
                sessionHostModel,
                aspireHostServiceLifetime
            )
            sendServiceChangedEvent(hostService)
        }, {
            hostService.stopHost()
            sendServiceChangedEvent(hostService)
        })

        withContext(Dispatchers.EDT) {
            sessionHostModel.resources.view(aspireHostServiceLifetime) { resourceLifetime, resourceId, resource ->
                viewResource(resourceId, resource, resourceLifetime, hostService)
            }
        }
    }

    private fun sendServiceChangedEvent(host: AspireHostService) {
        val event = ServiceEventListener.ServiceEvent.createEvent(
            ServiceEventListener.EventType.SERVICE_CHANGED,
            host,
            AspireServiceContributor::class.java
        )
        serviceEventPublisher.handle(event)
    }

    private fun viewResource(
        resourceId: String,
        resource: ResourceWrapper,
        resourceLifetime: Lifetime,
        hostService: AspireHostService
    ) {
        LOG.trace("Adding a new resource $resourceId")

        val resourcesByHost = resourceServices[hostService.projectPathString] ?: return

        val resourceService = AspireResourceService(resource, resourceLifetime, hostService, project)
        resourcesByHost.addUnique(resourceLifetime, resourceId, resourceService)

        val serviceViewManager = ServiceViewManager.getInstance(project)
        application.invokeLater {
            serviceViewManager.expand(hostService, AspireServiceContributor::class.java)
        }

        resource.isInitialized.set(true)

        resourceLifetime.bracketIfAlive({
            sendServiceStructureChangedEvent(hostService)
        }, {
            sendServiceStructureChangedEvent(hostService)
        })
    }

    private fun sendServiceStructureChangedEvent(host: AspireHostService) {
        val serviceEvent = ServiceEventListener.ServiceEvent.createEvent(
            ServiceEventListener.EventType.SERVICE_STRUCTURE_CHANGED,
            host,
            AspireServiceContributor::class.java
        )
        project.messageBus.syncPublisher(ServiceEventListener.TOPIC).handle(serviceEvent)
    }

    class Listener(private val project: Project) : RunManagerListener {
        override fun runConfigurationAdded(settings: RunnerAndConfigurationSettings) {
            val configuration = settings.configuration
            if (configuration !is AspireHostConfiguration) return
            val params = configuration.parameters
            val projectPath = Path(params.projectFilePath)
            val name = projectPath.nameWithoutExtension
            val host = AspireHostService(name, projectPath)
            getInstance(project).addAspireHostService(host)
        }

        override fun runConfigurationChanged(settings: RunnerAndConfigurationSettings) {
            val configuration = settings.configuration
            if (configuration !is AspireHostConfiguration) return
            val params = configuration.parameters
            val projectPath = Path(params.projectFilePath)
            val name = projectPath.nameWithoutExtension
            getInstance(project).updateAspireHostService(projectPath, name)
        }

        override fun runConfigurationRemoved(settings: RunnerAndConfigurationSettings) {
            val configuration = settings.configuration
            if (configuration !is AspireHostConfiguration) return
            val params = configuration.parameters
            val projectPath = Path(params.projectFilePath)
            getInstance(project).removeAspireHostService(projectPath)
        }
    }
}