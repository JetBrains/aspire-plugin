@file:Suppress("UnstableApiUsage")

package com.jetbrains.rider.aspire.services

import com.intellij.execution.services.ServiceEventListener
import com.intellij.execution.services.ServiceViewManager
import com.intellij.execution.services.ServiceViewProvidingContributor
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.application
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.rd.framework.*
import com.jetbrains.rd.protocol.IdeRootMarshallersProvider
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.SequentialLifetimes
import com.jetbrains.rdclient.protocol.RdDispatcher
import com.jetbrains.rider.aspire.generated.AspireHostModel
import com.jetbrains.rider.aspire.generated.AspireHostModelConfig
import com.jetbrains.rider.aspire.generated.AspireSessionHostModel
import com.jetbrains.rider.aspire.generated.aspireSessionHostModel
import com.jetbrains.rider.aspire.sessionHost.SessionHostConfig
import com.jetbrains.rider.aspire.sessionHost.SessionHostLauncher
import com.jetbrains.rider.util.NetUtils
import kotlinx.coroutines.CoroutineScope
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
    private val scope: CoroutineScope,
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

    private val descriptor by lazy { SessionHostServiceViewDescriptor() }

    private val aspireHosts = ConcurrentHashMap<Path, AspireHost>()

    private val isActive: Boolean
        get() = !sessionHostLifetimes.isTerminated
    val hasAspireHosts: Boolean
        get() = aspireHosts.any()
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
        if (isActive) return

        mutex.withLock {
            if (isActive) return

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

    private suspend fun subscribeToModel(
        sessionHostModel: AspireSessionHostModel,
        lifetime: Lifetime
    ) {
        LOG.trace("Subscribing to Session host model")

        withContext(Dispatchers.EDT) {
            sessionHostModel.aspireHosts.view(lifetime) { hostLifetime, hostId, hostModel ->
                viewAspireHost(hostModel, hostLifetime)
            }
        }
    }

    private fun viewAspireHost(model: AspireHostModel, aspireHostLifetime: Lifetime) {
        val aspireHostProjectPath = Path(model.config.aspireHostProjectPath)
        val aspireHost = aspireHosts[aspireHostProjectPath]
        if (aspireHost == null) {
            LOG.warn("Could not find Aspire host $aspireHostProjectPath")
            return
        }

        LOG.trace { "Setting Aspire host model to $aspireHostProjectPath" }
        aspireHost.setAspireHostModel(model, aspireHostLifetime)
    }

    suspend fun stop() {
        if (!isActive) return

        mutex.withLock {
            if (!isActive) return

            LOG.trace("Stopping session host")
            model = null
            debugSessionToken = null
            debugSessionPort = null
            sessionHostLifetimes.terminateCurrent()
        }
    }

    fun addAspireHostProject(aspireHostProjectPath: Path) {
        if (aspireHosts.containsKey(aspireHostProjectPath)) return

        LOG.trace { "Adding a new Aspire host ${aspireHostProjectPath.absolutePathString()}" }

        val aspireHost = AspireHost(aspireHostProjectPath, scope.childScope("Aspire Host"), project)
        Disposer.register(this, aspireHost)

        aspireHosts[aspireHostProjectPath] = aspireHost

        sendServiceAddedEvent(aspireHost)

        expand()
    }

    fun removeAspireHostProject(aspireHostProjectPath: Path) {
        LOG.trace { "Removing the Aspire host ${aspireHostProjectPath.absolutePathString()}" }

        val aspireHost = aspireHosts.remove(aspireHostProjectPath) ?: return

        sendServiceRemovedEvent(aspireHost)

        Disposer.dispose(aspireHost)
    }

    @RequiresEdt
    fun startAspireHostModel(config: AspireHostModelConfig) {
        if (!isActive) {
            LOG.warn("Unable to add Aspire host model because Session host isn't active")
            return
        }

        LOG.trace { "Adding Aspire host model $config" }
        requireNotNull(model).aspireHosts[config.id] = AspireHostModel(config)
    }

    @RequiresEdt
    fun stopAspireHostModel(aspireHostId: String) {
        if (!isActive) {
            LOG.warn("Unable to remove Aspire host model because Session host isn't active")
            return
        }

        LOG.trace { "Removing Aspire host model with id $aspireHostId" }
        requireNotNull(model).aspireHosts.remove(aspireHostId)
    }

    private fun expand() {
        application.invokeLater {
            ServiceViewManager
                .getInstance(project)
                .expand(this, AspireMainServiceViewContributor::class.java)
        }
    }

    private fun sendServiceAddedEvent(aspireHost: AspireHost) {
        val event = ServiceEventListener.ServiceEvent.createServiceAddedEvent(
            aspireHost,
            AspireMainServiceViewContributor::class.java,
            this
        )
        project.messageBus.syncPublisher(ServiceEventListener.TOPIC).handle(event)
    }

    private fun sendServiceRemovedEvent(aspireHost: AspireHost) {
        val event = ServiceEventListener.ServiceEvent.createEvent(
            ServiceEventListener.EventType.SERVICE_REMOVED,
            aspireHost,
            AspireMainServiceViewContributor::class.java
        )
        project.messageBus.syncPublisher(ServiceEventListener.TOPIC).handle(event)
    }

    override fun dispose() {
    }
}