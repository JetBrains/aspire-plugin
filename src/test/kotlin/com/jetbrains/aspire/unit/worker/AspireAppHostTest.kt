package com.jetbrains.aspire.unit.worker

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.TestApplicationManager
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.replaceService
import com.jetbrains.aspire.generated.dashboard.Resource
import com.jetbrains.aspire.generated.dashboard.Url
import com.jetbrains.aspire.generated.dashboard.UrlDisplayProperties
import com.jetbrains.aspire.resources.AspireResourceChange
import com.jetbrains.aspire.resources.AspireResourceUpdate
import com.jetbrains.aspire.resources.grpc.toAspireResourceData
import com.jetbrains.aspire.worker.AppHostListener
import com.jetbrains.aspire.worker.AppHostLogEntry
import com.jetbrains.aspire.worker.AspireAppHost
import com.jetbrains.aspire.worker.AspireAppHostId
import com.jetbrains.aspire.worker.AspireAppHostPath
import com.jetbrains.aspire.resources.grpc.GrpcResourceClientFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class AspireAppHostTest {
    private lateinit var testRootDisposable: Disposable
    private lateinit var mockFactory: MockGrpcResourceClientFactory
    private lateinit var hostScope: CoroutineScope

    private val project get() = ProjectManager.getInstance().defaultProject
    private val appHostPath = Path.of("test/path/AppHost.csproj")

    @BeforeAll
    fun setUpApplication() {
        TestApplicationManager.getInstance()
    }

    @BeforeEach
    fun setUpService() {
        testRootDisposable = Disposer.newDisposable("AspireAppHostTest")
        mockFactory = MockGrpcResourceClientFactory()
        hostScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        ApplicationManager.getApplication().replaceService(
            GrpcResourceClientFactory::class.java,
            mockFactory,
            testRootDisposable
        )
    }

    @AfterEach
    fun tearDown() {
        Disposer.dispose(testRootDisposable)
        hostScope.cancel()
    }

    @Test
    fun `dashboard URL is initially absent`() = timeoutRunBlocking {
        val host = createAppHost()

        assertEquals(null, host.aspireDashboardUrl.value)
    }

    @Test
    fun `unrelated resources do not set the dashboard URL`() = timeoutRunBlocking {
        val host = createAppHost()
        val environment = createDefaultEnvironment()
        val client = startAppHostAndWaitForResourceClient(host, environment)
        val other = resource("other", "https://other.example")
        val update = upsert(other)

        client.resourceUpdates.emit(update)

        withTimeout(10.seconds) {
            host.rootResources.first { resources -> resources.any { it.resourceName == "other" } }
        }
        assertEquals(null, host.aspireDashboardUrl.value)
    }

    @Test
    fun `dashboard URL prefers HTTPS over HTTP`() = timeoutRunBlocking {
        val host = createAppHost()
        val environment = createDefaultEnvironment()
        val client = startAppHostAndWaitForResourceClient(host, environment)
        val dashboard = resource("aspire-dashboard", "http://dashboard.example/one", "https://dashboard.example/secure")
        val update = upsert(dashboard)

        client.resourceUpdates.emit(update)

        host.awaitDashboardUrl("https://dashboard.example/secure")
    }

    @Test
    fun `dashboard resource is selected by display name rather than identifier`() = timeoutRunBlocking {
        val host = createAppHost()
        val environment = createDefaultEnvironment()
        val client = startAppHostAndWaitForResourceClient(host, environment)
        val other = resource("aspire-dashboard", "https://other.example", displayName = "other")
        val otherUpdate = upsert(other)
        client.resourceUpdates.emit(otherUpdate)
        withTimeout(10.seconds) {
            host.rootResources.first { resources -> resources.any { it.resourceName == "aspire-dashboard" } }
        }

        val dashboard = resource("dashboard-instance", "https://dashboard.example", displayName = "aspire-dashboard")
        val update = upsert(dashboard)
        client.resourceUpdates.emit(update)

        host.awaitDashboardUrl("https://dashboard.example")
    }

    @Test
    fun `dashboard URL is cleared when no URL name contains dashboard`() = timeoutRunBlocking {
        val host = createAppHost()
        val environment = createDefaultEnvironment()
        val client = startAppHostAndWaitForResourceClient(host, environment)
        val dashboard = resource("aspire-dashboard", "https://dashboard.example/selected")
        client.resourceUpdates.emit(upsert(dashboard))
        host.awaitDashboardUrl("https://dashboard.example/selected")

        val otherUrl = resource("aspire-dashboard", "https://dashboard.example/other", urlNames = listOf("Metrics"))
        client.resourceUpdates.emit(upsert(otherUrl))

        host.awaitDashboardUrl(null)
    }

    @Test
    fun `dashboard URL follows resource updates`() = timeoutRunBlocking {
        val host = createAppHost()
        val environment = createDefaultEnvironment()
        val client = startAppHostAndWaitForResourceClient(host, environment)
        val initialDashboard = resource("aspire-dashboard", "https://dashboard.example/secure")
        val initialUpdate = upsert(initialDashboard)
        client.resourceUpdates.emit(initialUpdate)
        host.awaitDashboardUrl("https://dashboard.example/secure")

        val newDashboard = resource("aspire-dashboard", "https://dashboard.example/new")
        val newUpdate = upsert(newDashboard)
        client.resourceUpdates.emit(newUpdate)

        host.awaitDashboardUrl("https://dashboard.example/new")
    }

    @Test
    fun `dashboard resource retains its instance on update`() = timeoutRunBlocking {
        val host = createAppHost()
        val environment = createDefaultEnvironment()
        val client = startAppHostAndWaitForResourceClient(host, environment)
        val initialDashboard = resource("aspire-dashboard", "https://dashboard.example/secure")
        val initialUpdate = upsert(initialDashboard)
        client.resourceUpdates.emit(initialUpdate)
        host.awaitDashboardUrl("https://dashboard.example/secure")
        val dashboard = host.rootResources.value.single { it.resourceName == "aspire-dashboard" }

        val newDashboard = resource("aspire-dashboard", "https://dashboard.example/new")
        val newUpdate = upsert(newDashboard)
        client.resourceUpdates.emit(newUpdate)
        host.awaitDashboardUrl("https://dashboard.example/new")

        assertSame(dashboard, host.rootResources.value.single { it.resourceName == "aspire-dashboard" })
    }

    @Test
    fun `dashboard URL falls back to HTTP when HTTPS is removed`() = timeoutRunBlocking {
        val host = createAppHost()
        val environment = createDefaultEnvironment()
        val client = startAppHostAndWaitForResourceClient(host, environment)
        val secureDashboard = resource("aspire-dashboard", "https://dashboard.example/secure")
        val secureUpdate = upsert(secureDashboard)
        client.resourceUpdates.emit(secureUpdate)
        host.awaitDashboardUrl("https://dashboard.example/secure")

        val fallbackDashboard = resource("aspire-dashboard", "http://dashboard.example/fallback")
        val fallbackUpdate = upsert(fallbackDashboard)
        client.resourceUpdates.emit(fallbackUpdate)

        host.awaitDashboardUrl("http://dashboard.example/fallback")
    }

    @Test
    fun `dashboard URL ignores unsupported schemes`() = timeoutRunBlocking {
        val host = createAppHost()
        val environment = createDefaultEnvironment()
        val client = startAppHostAndWaitForResourceClient(host, environment)
        val secureDashboard = resource("aspire-dashboard", "https://dashboard.example/secure")
        val secureUpdate = upsert(secureDashboard)
        client.resourceUpdates.emit(secureUpdate)
        host.awaitDashboardUrl("https://dashboard.example/secure")

        val unsupportedDashboard = resource("aspire-dashboard", "grpc://dashboard.example", "file:///dashboard")
        val unsupportedUpdate = upsert(unsupportedDashboard)
        client.resourceUpdates.emit(unsupportedUpdate)

        host.awaitDashboardUrl(null)
    }

    @Test
    fun `deleting the dashboard resource clears its URL`() = timeoutRunBlocking {
        val host = createAppHost()
        val environment = createDefaultEnvironment()
        val client = startAppHostAndWaitForResourceClient(host, environment)
        val dashboard = resource("aspire-dashboard", "https://dashboard.example/secure")
        val update = upsert(dashboard)
        client.resourceUpdates.emit(update)
        host.awaitDashboardUrl("https://dashboard.example/secure")

        val deletion = delete("aspire-dashboard")
        client.resourceUpdates.emit(deletion)

        host.awaitDashboardUrl(null)
    }

    @Test
    fun `stopping the host clears the dashboard URL`() = timeoutRunBlocking {
        val host = createAppHost()
        val environment = createDefaultEnvironment()
        val client = startAppHostAndWaitForResourceClient(host, environment)
        val dashboard = resource("aspire-dashboard", "https://dashboard.example")
        val update = upsert(dashboard)
        client.resourceUpdates.emit(update)
        host.awaitDashboardUrl("https://dashboard.example")

        stopAppHost(host)

        host.awaitDashboardUrl(null)
    }

    @Test
    fun `resource client is created with the started host environment`() = timeoutRunBlocking {
        val host = createAppHost()
        val environment = AspireAppHost.AppHostEnvironment("http://localhost:18889", "resource-api-key", null)

        val client = startAppHostAndWaitForResourceClient(host, environment)

        assertEquals(listOf(environment.resourceServiceEndpointUrl), mockFactory.endpointUrls)
        assertEquals(listOf(environment.resourceServiceApiKey), mockFactory.apiKeys)
        assertEquals(listOf(client), mockFactory.clientsFlow.value)
        assertFalse(client.isShutdown.value)
    }

    @Test
    fun `starting without a resource endpoint does not create a client`() = timeoutRunBlocking {
        val host = createAppHost()
        val emptyEnvironment = AspireAppHost.AppHostEnvironment.EMPTY
        val environment = AspireAppHost.AppHostEnvironment("http://localhost:18888", null, null)

        startAppHost(host, emptyEnvironment)
        stopAppHost(host)
        val client = startAppHostAndWaitForResourceClient(host, environment)

        assertEquals(listOf(client), mockFactory.clientsFlow.value)
        assertEquals(listOf(environment.resourceServiceEndpointUrl), mockFactory.endpointUrls)
        assertEquals(listOf<String?>(null), mockFactory.apiKeys)
    }

    @Test
    fun `disposing the host closes the resource client and clears resources`() = timeoutRunBlocking {
        val host = createAppHost()
        val environment = createDefaultEnvironment()
        val client = startAppHostAndWaitForResourceClient(host, environment)
        val dashboard = resource("aspire-dashboard", "https://dashboard.example")
        val update = upsert(dashboard)
        client.resourceUpdates.emit(update)
        host.awaitDashboardUrl("https://dashboard.example")
        var resourcesClearedAtShutdown: Boolean? = null
        client.onShutdown = { resourcesClearedAtShutdown = host.rootResources.value.isEmpty() }
        client.awaitConsoleLogSubscriptions(1)

        Disposer.dispose(host)
        withTimeout(10.seconds) {
            client.isShutdown.first { it }
            client.resourceUpdates.subscriptionCount.first { it == 0 }
            host.rootResources.first { it.isEmpty() }
        }

        assertEquals(1, client.shutdownCount)
        assertTrue(host.rootResources.value.isEmpty())
        assertEquals(0, client.resourceSubscriptionsAtShutdown)
        assertEquals(0, client.consoleLogSubscriptionsAtShutdown)
        assertEquals(true, resourcesClearedAtShutdown)
    }

    @Test
    fun `starting again cancels subscriptions and clears resources before closing the old client`() =
        timeoutRunBlocking {
            val host = createAppHost()
            val defaultEnvironment = createDefaultEnvironment()
            val firstClient = startAppHostAndWaitForResourceClient(host, defaultEnvironment)
            val dashboard = resource("aspire-dashboard", "https://dashboard.example")
            val update = upsert(dashboard)
            firstClient.resourceUpdates.emit(update)
            host.awaitDashboardUrl("https://dashboard.example")
            firstClient.awaitConsoleLogSubscriptions(1)
            var resourcesClearedAtShutdown: Boolean? = null
            firstClient.onShutdown = { resourcesClearedAtShutdown = host.rootResources.value.isEmpty() }
            val environment = AspireAppHost.AppHostEnvironment("http://localhost:18889", null, null)

            val secondClient = startAppHostAndWaitForResourceClient(host, environment)

            assertNotSame(firstClient, secondClient)
            assertEquals(1, firstClient.shutdownCount)
            assertEquals(0, firstClient.resourceSubscriptionsAtShutdown)
            assertEquals(0, firstClient.consoleLogSubscriptionsAtShutdown)
            assertEquals(true, resourcesClearedAtShutdown)
            assertTrue(host.rootResources.value.isEmpty())
        }

    @Test
    fun `cancelling the parent scope cancels subscriptions before closing the resource client`() = timeoutRunBlocking {
        val host = createAppHost()
        val environment = createDefaultEnvironment()
        val client = startAppHostAndWaitForResourceClient(host, environment)
        val dashboard = resource("aspire-dashboard", "https://dashboard.example")
        val update = upsert(dashboard)
        client.resourceUpdates.emit(update)
        host.awaitDashboardUrl("https://dashboard.example")
        client.awaitConsoleLogSubscriptions(1)
        var resourcesClearedAtShutdown: Boolean? = null
        client.onShutdown = { resourcesClearedAtShutdown = host.rootResources.value.isEmpty() }

        hostScope.cancel()
        withTimeout(10.seconds) {
            client.isShutdown.first { it }
        }

        assertEquals(1, client.shutdownCount)
        assertEquals(0, client.resourceSubscriptionsAtShutdown)
        assertEquals(0, client.consoleLogSubscriptionsAtShutdown)
        assertEquals(true, resourcesClearedAtShutdown)
    }

    @Test
    fun `restarting the host creates a new client and observes its resources`() = timeoutRunBlocking {
        val host = createAppHost()
        val defaultEnvironment = createDefaultEnvironment()
        val firstClient = startAppHostAndWaitForResourceClient(host, defaultEnvironment)
        val firstDashboard = resource("aspire-dashboard", "https://dashboard.example/first")
        val firstUpdate = upsert(firstDashboard)
        firstClient.resourceUpdates.emit(firstUpdate)
        host.awaitDashboardUrl("https://dashboard.example/first")
        stopAppHost(host)
        withTimeout(10.seconds) {
            firstClient.isShutdown.first { it }
            host.rootResources.first { it.isEmpty() }
        }
        val environment = AspireAppHost.AppHostEnvironment("http://localhost:18889", null, null)
        val secondDashboard = resource("aspire-dashboard", "https://dashboard.example/second")
        val secondUpdate = upsert(secondDashboard)

        val secondClient = startAppHostAndWaitForResourceClient(host, environment)
        secondClient.resourceUpdates.emit(secondUpdate)
        host.awaitDashboardUrl("https://dashboard.example/second")

        assertNotSame(firstClient, secondClient)
        assertEquals(listOf(firstClient, secondClient), mockFactory.clientsFlow.value)
        assertEquals(listOf("http://localhost:18888", "http://localhost:18889"), mockFactory.endpointUrls)
        assertEquals(listOf("test-key", null), mockFactory.apiKeys)
        assertEquals(1, firstClient.shutdownCount)
        assertEquals(0, firstClient.resourceUpdates.subscriptionCount.value)
        assertFalse(secondClient.isShutdown.value)
    }

    private fun createAppHost(): AspireAppHost {
        val id = AspireAppHostId("AppHost")
        return AspireAppHost(id, "AppHost", appHostPath, project, hostScope).also {
            Disposer.register(testRootDisposable, it)
        }
    }

    private suspend fun startAppHost(host: AspireAppHost, environment: AspireAppHost.AppHostEnvironment) {
        project.messageBus.syncPublisher(AppHostListener.TOPIC)
            .appHostStarting(appHostPath, environment)

        val messages = MutableSharedFlow<AppHostLogEntry>()
        project.messageBus.syncPublisher(AppHostListener.TOPIC)
            .appHostStarted(appHostPath, messages)

        withTimeout(10.seconds) {
            host.appHostState.first { it is AspireAppHost.AspireAppHostState.Started }
        }
    }

    private suspend fun stopAppHost(host: AspireAppHost) {
        project.messageBus.syncPublisher(AppHostListener.TOPIC)
            .appHostStopped(appHostPath)

        withTimeout(10.seconds) {
            host.appHostState.first { it is AspireAppHost.AspireAppHostState.Stopped }
        }
    }

    private fun createDefaultEnvironment(): AspireAppHost.AppHostEnvironment = AspireAppHost.AppHostEnvironment(
        "http://localhost:18888",
        "test-key",
        null
    )

    private suspend fun startAppHostAndWaitForResourceClient(
        host: AspireAppHost,
        environment: AspireAppHost.AppHostEnvironment,
    ): MockAspireResourceClient {
        val previousClientCount = mockFactory.clientsFlow.value.size
        startAppHost(host, environment)
        return withTimeout(10.seconds) {
            val client = mockFactory.clientsFlow.first { it.size > previousClientCount }.last()
            client.resourceUpdates.subscriptionCount.first { it == 1 }
            client
        }
    }

    private suspend fun MockAspireResourceClient.awaitConsoleLogSubscriptions(expected: Int) {
        withTimeout(10.seconds) {
            consoleLogSubscriptionCountFlow.first { it == expected }
        }
    }

    private suspend fun AspireAppHost.awaitDashboardUrl(expected: String?) {
        withTimeout(10.seconds) { aspireDashboardUrl.first { it == expected } }
        assertEquals(expected, aspireDashboardUrl.value)
    }

    private fun resource(
        name: String,
        vararg urls: String,
        displayName: String = name,
        urlNames: List<String> = List(urls.size) { "Dashboard" }
    ): Resource {
        val resourceUrls = urls.mapIndexed { index, url ->
            val displayProperties = UrlDisplayProperties.newBuilder()
                .setDisplayName(urlNames[index])
                .build()
            Url.newBuilder()
                .setFullUrl(url)
                .setDisplayProperties(displayProperties)
                .build()
        }
        return Resource.newBuilder()
            .setName(name)
            .setResourceType("Project")
            .setDisplayName(displayName)
            .setUid("uid-$name")
            .setState("Running")
            .addAllUrls(resourceUrls)
            .build()
    }

    private fun upsert(resource: Resource): AspireResourceUpdate {
        val hostPath = AspireAppHostPath(appHostPath.toAbsolutePath().toString())
        val data = resource.toAspireResourceData(hostPath)
        val change = AspireResourceChange.Upsert(data)
        val changes = listOf(change)
        return AspireResourceUpdate.Changes(changes)
    }

    @Suppress("SameParameterValue")
    private fun delete(resourceName: String): AspireResourceUpdate {
        val change = AspireResourceChange.Delete(resourceName)
        val changes = listOf(change)
        return AspireResourceUpdate.Changes(changes)
    }
}
