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
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.aspire.generated.AspireSessionHostModel
import com.jetbrains.rider.aspire.listeners.AspireSessionHostModelListener
import com.jetbrains.rider.aspire.run.AspireHostConfiguration
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

@Service(Service.Level.PROJECT)
class AspireHostManager(project: Project) : Disposable {
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

    fun getAspireHost(aspireHostProjectPathString: String) = aspireHosts[aspireHostProjectPathString]

    fun addAspireHost(aspireHost: AspireHost) {
        Disposer.register(this, aspireHost)

        if (aspireHosts.containsKey(aspireHost.hostProjectPathString)) return

        LOG.trace("Adding a new Aspire host ${aspireHost.hostProjectPathString}")
        aspireHosts[aspireHost.hostProjectPathString] = aspireHost

        val event = ServiceEventListener.ServiceEvent.createEvent(
            ServiceEventListener.EventType.SERVICE_ADDED,
            aspireHost,
            AspireMainServiceViewContributor::class.java
        )
        serviceEventPublisher.handle(event)
    }

    fun removeAspireHost(aspireHostProjectPath: Path) {
        val hostProjectPathString = aspireHostProjectPath.absolutePathString()
        LOG.trace("Removing the Aspire host $hostProjectPathString")

        val aspireHost = aspireHosts.remove(hostProjectPathString) ?: return

        val event = ServiceEventListener.ServiceEvent.createEvent(
            ServiceEventListener.EventType.SERVICE_REMOVED,
            aspireHost,
            AspireMainServiceViewContributor::class.java
        )
        serviceEventPublisher.handle(event)

        Disposer.dispose(aspireHost)
    }

    fun addSessionHostModel(
        aspireHostProjectPath: Path,
        sessionHostModel: AspireSessionHostModel,
        lifetime: Lifetime
    ) {
        val hostProjectPathString = aspireHostProjectPath.absolutePathString()
        val aspireHost = aspireHosts[hostProjectPathString]
        if (aspireHost == null) {
            LOG.warn("Unable to find Aspire host $hostProjectPathString")
            return
        }

        aspireHost.addSessionHostModel(sessionHostModel, lifetime)
    }

    override fun dispose() {
    }

    class RunListener(private val project: Project) : RunManagerListener {
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

    class SessionHostModelListener(private val project: Project) : AspireSessionHostModelListener {
        override fun modelCreated(
            aspireHostProjectPath: Path,
            sessionHostModel: AspireSessionHostModel,
            lifetime: Lifetime
        ) {
            getInstance(project).addSessionHostModel(aspireHostProjectPath, sessionHostModel, lifetime)
        }
    }
}