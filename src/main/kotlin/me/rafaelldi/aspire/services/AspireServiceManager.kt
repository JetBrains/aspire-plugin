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

    private val sessionHosts = ConcurrentHashMap<String, SessionHostServiceData>()
    private val notifier = project.messageBus.syncPublisher(ServiceEventListener.TOPIC)

    fun addSessionHost(
        sessionHostConfig: AspireSessionHostConfig,
        sessionHostModel: AspireSessionHostModel,
        sessionHostLifetime: Lifetime
    ) {
        val hostDate = SessionHostServiceData(
            sessionHostConfig.id,
            sessionHostConfig.hostName,
            sessionHostConfig.dashboardUrl,
            sessionHostModel
        )
        sessionHosts.addUnique(sessionHostLifetime, sessionHostConfig.id, hostDate)

        sessionHostModel.sessions.view(sessionHostLifetime) { sessionLifetime, _, _ ->
            sessionLifetime.bracketIfAlive(
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

        sessionHostLifetime.bracketIfAlive(
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

    fun getSessionHosts(): List<SessionHostServiceData> = sessionHosts.values.toList()

    class SessionHostLifecycleListener(val project: Project) : AspireSessionHostLifecycleListener {
        override fun sessionHostStarted(
            sessionHostConfig: AspireSessionHostConfig,
            sessionHostModel: AspireSessionHostModel,
            sessionHostLifetime: Lifetime
        ) {
            getInstance(project).addSessionHost(sessionHostConfig, sessionHostModel, sessionHostLifetime)
        }
    }
}