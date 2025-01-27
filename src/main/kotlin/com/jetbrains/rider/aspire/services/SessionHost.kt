@file:Suppress("UnstableApiUsage")

package com.jetbrains.rider.aspire.services

import com.intellij.execution.services.ServiceEventListener
import com.intellij.execution.services.ServiceViewProvidingContributor
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.jetbrains.rd.framework.*
import com.jetbrains.rd.protocol.IdeRootMarshallersProvider
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.SequentialLifetimes
import com.jetbrains.rdclient.protocol.RdDispatcher
import com.jetbrains.rider.aspire.generated.AspireHostModel
import com.jetbrains.rider.aspire.generated.AspireSessionHostModel
import com.jetbrains.rider.aspire.generated.aspireSessionHostModel
import com.jetbrains.rider.aspire.sessionHost.SessionHostConfig
import com.jetbrains.rider.aspire.sessionHost.SessionHostLauncher
import com.jetbrains.rider.util.NetUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

class SessionHost(
    lifetime: Lifetime,
    private val project: Project
) : ServiceViewProvidingContributor<AspireHost, SessionHost>, Disposable {
    companion object {
        private val LOG = logger<SessionHost>()
    }

    private val sessionHostLifetimes = SequentialLifetimes(lifetime).apply {
        terminateCurrent()
    }
    private val mutex = Mutex()

    private var model: AspireSessionHostModel? = null

    private val descriptor by lazy { SessionHostServiceViewDescriptor(this) }

    private val aspireHosts = ConcurrentHashMap<Path, AspireHost>()

    private val serviceEventPublisher = project.messageBus.syncPublisher(ServiceEventListener.TOPIC)

    val isActive: Boolean
        get() = !sessionHostLifetimes.isTerminated
    var debugSessionToken: String? = null
        private set
    var debugSessionPort: Int? = null
        private set

    override fun asService(): SessionHost = this

    override fun getViewDescriptor(project: Project) = descriptor

    override fun getServices(project: Project) = buildList {
        for (host in aspireHosts) {
            add(host.value)
        }
    }.sortedBy { it.displayName }

    override fun getServiceDescriptor(project: Project, aspireHost: AspireHost) = aspireHost.getViewDescriptor(project)

    fun getAspireHost(aspireHostProjectPath: Path): AspireHost? {
        return aspireHosts[aspireHostProjectPath]
    }

    suspend fun start() {
        if (!sessionHostLifetimes.isTerminated) return

        mutex.withLock {
            if (!sessionHostLifetimes.isTerminated) return

            LOG.trace("Starting session host")
            val sessionHostLifetime = sessionHostLifetimes.next()
            val protocol = startSessionHostProtocol(sessionHostLifetime.lifetime)
            model = protocol.aspireSessionHostModel

            subscribeToModel(protocol.aspireSessionHostModel, sessionHostLifetime.lifetime)

            debugSessionToken = UUID.randomUUID().toString()
            debugSessionPort = NetUtils.findFreePort(47100)

            val sessionHostConfig = SessionHostConfig(
                protocol.wire.serverPort,
                requireNotNull(debugSessionToken),
                requireNotNull(debugSessionPort)
            )
            LOG.trace { "Starting a new session host with launcher $sessionHostConfig" }
            val sessionHostLauncher = SessionHostLauncher.getInstance(project)
            sessionHostLauncher.launchSessionHost(sessionHostConfig, sessionHostLifetime)
        }
    }

    private suspend fun startSessionHostProtocol(lifetime: Lifetime) = withContext(Dispatchers.EDT) {
        val dispatcher = RdDispatcher(lifetime)
        val wire = SocketWire.Server(lifetime, dispatcher, null)
        val protocol = Protocol(
            "AspireSessionHost::protocol",
            Serializers(IdeRootMarshallersProvider),
            Identities(IdKind.Server),
            dispatcher,
            wire,
            lifetime
        )
        return@withContext protocol
    }

    private fun subscribeToModel(
        sessionHostModel: AspireSessionHostModel,
        lifetime: Lifetime
    ) {
        LOG.trace("Subscribing to session host protocol model")

        sessionHostModel.aspireHosts.view(lifetime) { hostLifetime, hostId, hostModel ->
            viewAspireHost(hostModel, hostLifetime)
        }
    }

    private fun viewAspireHost(model: AspireHostModel, lifetime: Lifetime) {
        val aspireHostProjectPath = Path(model.config.aspireHostProjectPath)
        val aspireHost = aspireHosts[aspireHostProjectPath]
        if (aspireHost == null) {
            LOG.warn("Could not find aspire host $aspireHostProjectPath")
            return
        }

        LOG.trace { "Setting Aspire host model to $aspireHostProjectPath" }
        aspireHost.setAspireHostModel(model, lifetime)
    }

    suspend fun stop() {
        if (sessionHostLifetimes.isTerminated) return

        mutex.withLock {
            if (sessionHostLifetimes.isTerminated) return

            LOG.trace("Stopping session host")
            model = null
            debugSessionToken = null
            debugSessionPort = null
            sessionHostLifetimes.terminateCurrent()
        }
    }

    fun addAspireHost(aspireHostProjectPath: Path) {
        if (aspireHosts.containsKey(aspireHostProjectPath)) return

        LOG.trace { "Adding a new Aspire host ${aspireHostProjectPath.absolutePathString()}" }

        val aspireHost = AspireHost(aspireHostProjectPath, project)
        Disposer.register(this, aspireHost)

        aspireHosts[aspireHostProjectPath] = aspireHost

        val event = ServiceEventListener.ServiceEvent.createEvent(
            ServiceEventListener.EventType.RESET,
            aspireHost,
            AspireMainServiceViewContributor::class.java
        )
        serviceEventPublisher.handle(event)
    }

    fun removeAspireHost(aspireHostProjectPath: Path) {
        LOG.trace { "Removing the Aspire host ${aspireHostProjectPath.absolutePathString()}" }

        val aspireHost = aspireHosts.remove(aspireHostProjectPath) ?: return

        val event = ServiceEventListener.ServiceEvent.createEvent(
            ServiceEventListener.EventType.SERVICE_REMOVED,
            aspireHost,
            AspireMainServiceViewContributor::class.java
        )
        serviceEventPublisher.handle(event)

        Disposer.dispose(aspireHost)
    }

    override fun dispose() {
    }
}