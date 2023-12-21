package me.rafaelldi.aspire.services

import com.intellij.execution.services.ServiceEventListener
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.addUnique
import com.jetbrains.rd.util.lifetime.Lifetime
import me.rafaelldi.aspire.generated.AspireSessionHostModel
import me.rafaelldi.aspire.sessionHost.AspireSessionHostConfig
import me.rafaelldi.aspire.sessionHost.AspireSessionHostLifecycleListener
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class AspireServiceManager(project: Project) {
    companion object {
        fun getInstance(project: Project) = project.service<AspireServiceManager>()
    }

    private val hosts = ConcurrentHashMap<String, HostServiceData>()
    private val hostServices = ConcurrentHashMap<String, MutableList<String>>()
    private val notifier = project.messageBus.syncPublisher(ServiceEventListener.TOPIC)

    fun addHost(
        hostConfig: AspireSessionHostConfig,
        hostModel: AspireSessionHostModel,
        hostLifetime: Lifetime
    ) {
        val hostDate = HostServiceData(hostConfig.id, hostConfig.hostName, hostConfig.dashboardUrl)
        hosts.addUnique(hostLifetime, hostConfig.id, hostDate)
        hostServices.addUnique(hostLifetime, hostConfig.id, mutableListOf())

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

    fun getHosts(): List<HostServiceData> = hosts.values.toList()

    data class HostServiceData(val id: String, val hostName: String, val dashboardUrl: String?)

    class HostLifecycleListener(val project: Project) : AspireSessionHostLifecycleListener {
        override fun sessionHostStarted(
            hostConfig: AspireSessionHostConfig,
            hostModel: AspireSessionHostModel,
            hostLifetime: Lifetime
        ) {
            getInstance(project).addHost(hostConfig, hostModel, hostLifetime)
        }
    }
}