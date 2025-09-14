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
import com.intellij.util.application
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.rd.framework.*
import com.jetbrains.rd.protocol.IdeRootMarshallersProvider
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.SequentialLifetimes
import com.jetbrains.rdclient.protocol.RdDispatcher
import com.jetbrains.rider.aspire.generated.AspireHostModel
import com.jetbrains.rider.aspire.generated.AspireHostModelConfig
import com.jetbrains.rider.aspire.generated.AspireWorkerModel
import com.jetbrains.rider.aspire.generated.aspireWorkerModel
import com.jetbrains.rider.aspire.settings.AspireSettings
import com.jetbrains.rider.aspire.util.DEBUG_SESSION_PORT
import com.jetbrains.rider.aspire.util.DEBUG_SESSION_SERVER_CERTIFICATE
import com.jetbrains.rider.aspire.util.DEBUG_SESSION_TOKEN
import com.jetbrains.rider.aspire.util.checkDevCertificate
import com.jetbrains.rider.aspire.util.exportCertificate
import com.jetbrains.rider.aspire.worker.AspireWorkerConfig
import com.jetbrains.rider.aspire.worker.AspireWorkerLauncher
import com.jetbrains.rider.util.NetUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.set
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

class AspireWorker(
    lifetime: Lifetime,
    private val project: Project
) : ServiceViewProvidingContributor<AspireHost, AspireWorker>, Disposable {
    companion object {
        private val LOG = logger<AspireWorker>()
    }

    private val workerLifetimes = SequentialLifetimes(lifetime).apply {
        terminateCurrent()
    }
    private val mutex = Mutex()

    private var model: AspireWorkerModel? = null

    private val descriptor by lazy { AspireWorkerServiceViewDescriptor() }

    private val aspireHosts = ConcurrentHashMap<Path, AspireHost>()

    private val isActive: Boolean
        get() = !workerLifetimes.isTerminated
    val hasAspireHosts: Boolean
        get() = aspireHosts.any()

    private var debugSessionToken: String? = null
    private var debugSessionPort: Int? = null
    private var debugSessionServerCertificate: String? = null

    override fun asService(): AspireWorker = this

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

    //Switch DCP to the IDE mode
    //see: https://github.com/dotnet/aspire/blob/main/docs/specs/IDE-execution.md#enabling-ide-execution
    fun getEnvironmentVariablesForDcpConnection() = buildMap {
        val debugSessionToken = requireNotNull(debugSessionToken)
        val debugSessionPort = requireNotNull(debugSessionPort)
        val debugSessionServerCertificate = debugSessionServerCertificate

        put(DEBUG_SESSION_TOKEN, debugSessionToken)
        put(DEBUG_SESSION_PORT, "localhost:$debugSessionPort")
        debugSessionServerCertificate?.also { put(DEBUG_SESSION_SERVER_CERTIFICATE, it) }
    }

    suspend fun start() {
        if (isActive) return

        mutex.withLock {
            if (isActive) return

            LOG.trace("Starting Aspire worker")
            val workerLifetime = workerLifetimes.next()

            val protocol = startAspireWorkerProtocol(workerLifetime.lifetime)

            subscribeToModel(protocol.aspireWorkerModel, workerLifetime.lifetime)

            val token = UUID.randomUUID().toString()
            val port = NetUtils.findFreePort(47100)
            val certificate = calculateServerCertificate(workerLifetime)

            workerLifetime.bracketIfAlive({
                model = protocol.aspireWorkerModel
                debugSessionToken = token
                debugSessionPort = port
                debugSessionServerCertificate = certificate
            }, {
                model = null
                debugSessionToken = null
                debugSessionPort = null
                debugSessionServerCertificate = null
            })

            val aspireWorkerConfig = AspireWorkerConfig(
                protocol.wire.serverPort,
                token,
                port,
                certificate != null
            )
            LOG.trace { "Starting a new session host with launcher $aspireWorkerConfig" }
            val aspireWorkerLauncher = AspireWorkerLauncher.getInstance(project)
            aspireWorkerLauncher.launchWorker(aspireWorkerConfig, workerLifetime)
        }
    }

    private suspend fun startAspireWorkerProtocol(lifetime: Lifetime) = withContext(Dispatchers.EDT) {
        val dispatcher = RdDispatcher(lifetime)
        val wire = SocketWire.Server(lifetime, dispatcher, null)
        val protocol = Protocol(
            "AspireWorker::protocol",
            Serializers(IdeRootMarshallersProvider),
            Identities(IdKind.Server),
            dispatcher,
            wire,
            lifetime
        )
        return@withContext protocol
    }

    private suspend fun subscribeToModel(aspireWorkerModel: AspireWorkerModel, lifetime: Lifetime) {
        LOG.trace("Subscribing to Aspire worker model")

        withContext(Dispatchers.EDT) {
            aspireWorkerModel.aspireHosts.view(lifetime) { hostLifetime, _, hostModel ->
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

    private suspend fun calculateServerCertificate(workerLifetime: Lifetime): String? {
        if (!AspireSettings.getInstance().connectToDcpViaHttps) return null

        val hasTrustedCertificate = checkDevCertificate(workerLifetime, project)
        if (!hasTrustedCertificate) return null

        return exportCertificate(workerLifetime, project)
    }

    suspend fun stop() {
        if (!isActive) return

        mutex.withLock {
            if (!isActive) return

            LOG.trace("Stopping Aspire worker")
            workerLifetimes.terminateCurrent()
        }
    }

    fun addAspireHostProject(aspireHostProjectPath: Path) {
        if (aspireHosts.containsKey(aspireHostProjectPath)) return

        LOG.trace { "Adding a new Aspire host ${aspireHostProjectPath.absolutePathString()}" }

        val aspireHost = AspireHost(aspireHostProjectPath, project)
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