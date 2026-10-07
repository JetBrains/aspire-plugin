package com.jetbrains.aspire.unit.worker

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.TestApplicationManager
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.replaceService
import com.jetbrains.aspire.generated.dashboard.Resource
import com.jetbrains.aspire.generated.dashboard.ResourceDeletion
import com.jetbrains.aspire.generated.dashboard.Url
import com.jetbrains.aspire.generated.dashboard.UrlDisplayProperties
import com.jetbrains.aspire.generated.dashboard.WatchResourcesChange
import com.jetbrains.aspire.generated.dashboard.WatchResourcesChanges
import com.jetbrains.aspire.generated.dashboard.WatchResourcesUpdate
import com.jetbrains.aspire.worker.AppHostListener
import com.jetbrains.aspire.worker.AppHostLogEntry
import com.jetbrains.aspire.worker.AspireAppHost
import com.jetbrains.aspire.worker.AspireAppHostId
import com.jetbrains.aspire.resources.grpc.AspireDashboardClientFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AspireAppHostTest {
    private lateinit var testRootDisposable: Disposable
    private lateinit var mockFactory: MockAspireDashboardClientFactory
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
        mockFactory = MockAspireDashboardClientFactory()
        hostScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        ApplicationManager.getApplication().replaceService(
            AspireDashboardClientFactory::class.java,
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
        val client = startDashboardClient(host)
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
        val client = startDashboardClient(host)
        val dashboard = resource("aspire-dashboard", "http://dashboard.example/one", "https://dashboard.example/secure")
        val update = upsert(dashboard)

        client.resourceUpdates.emit(update)

        host.awaitDashboardUrl("https://dashboard.example/secure")
    }

    @Test
    fun `dashboard resource is selected by display name rather than identifier`() = timeoutRunBlocking {
        val host = createAppHost()
        val client = startDashboardClient(host)
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
        val client = startDashboardClient(host)
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
        val client = startDashboardClient(host)
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
        val client = startDashboardClient(host)
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
        val client = startDashboardClient(host)
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
        val client = startDashboardClient(host)
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
        val client = startDashboardClient(host)
        val dashboard = resource("aspire-dashboard", "https://dashboard.example/secure")
        val update = upsert(dashboard)
        client.resourceUpdates.emit(update)
        host.awaitDashboardUrl("https://dashboard.example/secure")

        val deletion = deleteDashboard()
        client.resourceUpdates.emit(deletion)

        host.awaitDashboardUrl(null)
    }

    @Test
    fun `stopping the host clears the dashboard URL`() = timeoutRunBlocking {
        val host = createAppHost()
        val client = startDashboardClient(host)
        val dashboard = resource("aspire-dashboard", "https://dashboard.example")
        val update = upsert(dashboard)
        client.resourceUpdates.emit(update)
        host.awaitDashboardUrl("https://dashboard.example")

        project.messageBus.syncPublisher(AppHostListener.TOPIC).appHostStopped(appHostPath)
        withTimeout(10.seconds) {
            client.isShutdown.first { it }
            host.rootResources.first { it.isEmpty() }
        }
        host.awaitDashboardUrl(null)
        assertTrue(host.rootResources.value.isEmpty())
    }

    private fun createAppHost(): AspireAppHost = AspireAppHost(
        AspireAppHostId("AppHost"),
        "AppHost",
        appHostPath,
        project,
        hostScope
    ).also {
        Disposer.register(testRootDisposable, it)
    }

    private suspend fun startDashboardClient(host: AspireAppHost): MockAspireDashboardClientApi {
        val environment = AspireAppHost.AppHostEnvironment("http://localhost:18888", "test-key", null)
        withTimeout(10.seconds) {
            while (host.appHostState.value !is AspireAppHost.AspireAppHostState.Starting) {
                project.messageBus.syncPublisher(AppHostListener.TOPIC).appHostStarting(appHostPath, environment)
                delay(10.milliseconds)
            }
        }
        val messages = MutableSharedFlow<AppHostLogEntry>()
        project.messageBus.syncPublisher(AppHostListener.TOPIC)
            .appHostStarted(appHostPath, messages)
        return withTimeout(10.seconds) {
            while (mockFactory.lastClient?.resourceUpdates?.subscriptionCount?.value != 1) delay(10.milliseconds)
            requireNotNull(mockFactory.lastClient)
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
            Url.newBuilder()
                .setFullUrl(url)
                .setDisplayProperties(UrlDisplayProperties.newBuilder().setDisplayName(urlNames[index]))
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

    private fun upsert(resource: Resource): WatchResourcesUpdate {
        val change = WatchResourcesChange.newBuilder().setUpsert(resource)
        val changes = WatchResourcesChanges.newBuilder().addValue(change)
        return WatchResourcesUpdate.newBuilder().setChanges(changes).build()
    }

    private fun deleteDashboard(): WatchResourcesUpdate {
        val deletion = ResourceDeletion.newBuilder()
            .setResourceName("aspire-dashboard")
            .setResourceType("Project")
        val change = WatchResourcesChange.newBuilder().setDelete(deletion)
        val changes = WatchResourcesChanges.newBuilder().addValue(change)
        return WatchResourcesUpdate.newBuilder().setChanges(changes).build()
    }
}
