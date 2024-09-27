package com.jetbrains.rider.aspire.services.a

import com.intellij.execution.RunManagerListener
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.services.ServiceEventListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.jetbrains.rider.aspire.run.AspireHostConfiguration
import com.jetbrains.rider.aspire.services.AspireServiceContributor
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

@Service(Service.Level.PROJECT)
class AspireHostManager(private val project: Project) : Disposable {
    companion object {
        fun getInstance(project: Project) = project.service<AspireHostManager>()

        private val LOG = logger<AspireHostManager>()
    }

    private val aspireHosts = ConcurrentHashMap<String, AspireHost>()

    private val serviceEventPublisher = project.messageBus.syncPublisher(ServiceEventListener.TOPIC)

    fun getAspireHosts(): List<AspireHost> {
        val hosts = mutableListOf<AspireHost>()
        for (host in aspireHosts) {
            hosts.add(host.value)
        }
        return hosts
    }

    fun addAspireHost(aspireHost: AspireHost) {
        if (aspireHosts.containsKey(aspireHost.hostProjectPathString)) return

        LOG.trace("Adding a new Aspire host ${aspireHost.hostProjectPathString}")
        aspireHosts.put(aspireHost.hostProjectPathString, aspireHost)
        Disposer.register(this, aspireHost)

        val event = ServiceEventListener.ServiceEvent.createEvent(
            ServiceEventListener.EventType.SERVICE_ADDED,
            aspireHost,
            AspireServiceContributor::class.java
        )
        serviceEventPublisher.handle(event)
    }

    fun removeAspireHost(aspireHostProjectPath: Path) {
        val hostProjectPathString = aspireHostProjectPath.absolutePathString()
        LOG.trace("Removing the Aspire host $hostProjectPathString")

        val aspireHost = aspireHosts.remove(hostProjectPathString)
        if (aspireHost == null) return

        val event = ServiceEventListener.ServiceEvent.createEvent(
            ServiceEventListener.EventType.SERVICE_REMOVED,
            aspireHost,
            AspireServiceContributor::class.java
        )
        serviceEventPublisher.handle(event)

        Disposer.dispose(aspireHost)
    }

    override fun dispose() {
    }

    class Listener(private val project: Project) : RunManagerListener {
        override fun runConfigurationAdded(settings: RunnerAndConfigurationSettings) {
            val configuration = settings.configuration
            if (configuration !is AspireHostConfiguration) return
            val params = configuration.parameters
            val projectPath = Path(params.projectFilePath)
            val aspireHost = AspireHost(projectPath, project)
            getInstance(project).addAspireHost(aspireHost)
        }

        override fun runConfigurationRemoved(settings: RunnerAndConfigurationSettings) {
            val configuration = settings.configuration
            if (configuration !is AspireHostConfiguration) return
            val params = configuration.parameters
            val projectPath = Path(params.projectFilePath)
            getInstance(project).removeAspireHost(projectPath)
        }
    }
}