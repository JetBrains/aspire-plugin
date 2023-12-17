package me.rafaelldi.aspire.services

import com.intellij.execution.services.ServiceEventListener
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.util.withUiContext
import com.jetbrains.rd.util.addUnique
import com.jetbrains.rd.util.lifetime.Lifetime
import me.rafaelldi.aspire.generated.AspireSessionHostModel
import me.rafaelldi.aspire.sessionHost.AspireHostConfig
import me.rafaelldi.aspire.sessionHost.AspireHostLifecycleListener
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class AspireServiceManager(project: Project) {
    companion object {
        fun getInstance(project: Project) = project.service<AspireServiceManager>()
    }

    private val hosts = ConcurrentHashMap<String, AspireHostConfig>()
    private val notifier = project.messageBus.syncPublisher(ServiceEventListener.TOPIC)

    fun addHost(
        hostConfig: AspireHostConfig,
        hostModel: AspireSessionHostModel,
        hostLifetime: Lifetime
    ) {
        hosts.addUnique(hostLifetime, hostConfig.id, hostConfig)

        hostModel.sessions.view(hostLifetime) { sessionLifetime, sessionId, sessionModel ->

        }

        hostLifetime.bracketIfAlive(
            {
                val event = ServiceEventListener.ServiceEvent.createResetEvent(AspireServiceContributor::class.java)
                notifier.handle(event)
            },
            {
                val event = ServiceEventListener.ServiceEvent.createResetEvent(AspireServiceContributor::class.java)
                notifier.handle(event)
            }
        )
    }

    fun getHosts(): List<AspireHostConfig> = hosts.values.toList()

    class HostLifecycleListener(val project: Project) : AspireHostLifecycleListener {
        override fun hostStarted(
            hostConfig: AspireHostConfig,
            hostModel: AspireSessionHostModel,
            hostLifetime: Lifetime
        ) {
            getInstance(project).addHost(hostConfig, hostModel, hostLifetime)
        }
    }
}