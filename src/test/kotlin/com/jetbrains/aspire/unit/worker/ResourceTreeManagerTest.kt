package com.jetbrains.aspire.unit.worker

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.TestApplicationManager
import com.jetbrains.aspire.generated.dashboard.Resource
import com.jetbrains.aspire.resources.AspireResourceChange
import com.jetbrains.aspire.resources.AspireResourceClient
import com.jetbrains.aspire.resources.AspireResourceCommandRequest
import com.jetbrains.aspire.resources.AspireResourceUpdate
import com.jetbrains.aspire.worker.AspireAppHostPath
import com.jetbrains.aspire.worker.AspireResource
import com.jetbrains.aspire.worker.ResourceListener
import com.jetbrains.aspire.worker.ResourceState
import com.jetbrains.aspire.worker.ResourceTreeManager
import com.jetbrains.aspire.resources.grpc.toAspireResourceData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
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

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class ResourceTreeManagerTest {
    private lateinit var testRootDisposable: Disposable

    private val project get() = ProjectManager.getInstance().defaultProject

    @BeforeAll
    fun setUpApplication() {
        TestApplicationManager.getInstance()
    }

    @BeforeEach
    fun setUp() {
        testRootDisposable = Disposer.newDisposable("ResourceTreeManagerTest")
    }

    @AfterEach
    fun tearDown() {
        Disposer.dispose(testRootDisposable)
    }

    // region Resource CRUD

    @Test
    fun `initial data creates root resources`() = runTest {
        val client = MockAspireResourceClient()
        val resourceClient = MutableStateFlow<AspireResourceClient?>(client)
        val manager = createResourceTreeManager(resourceClient)
        testScheduler.runCurrent()
        val resourceListener = connectListener()

        val resource1 = buildResource("res-1", "Resource 1")
        val resource2 = buildResource("res-2", "Resource 2")
        val initialData = buildInitialData(listOf(resource1, resource2))
        client.resourceUpdates.emit(initialData)
        testScheduler.runCurrent()

        val roots = manager.rootResources.value
        assertEquals(2, roots.size)
        assertEquals(resource1.name, roots[0].resourceName)
        assertEquals(resource2.name, roots[1].resourceName)
        assertEquals(2, resourceListener.created.size)
    }

    @Test
    fun `upsert creates new resource`() = runTest {
        val client = MockAspireResourceClient()
        val resourceClient = MutableStateFlow<AspireResourceClient?>(client)
        val manager = createResourceTreeManager(resourceClient)
        testScheduler.runCurrent()
        val resourceListener = connectListener()

        val resource1 = buildResource("res-1", "Resource 1")
        val update = buildUpsertUpdate(listOf(resource1))
        client.resourceUpdates.emit(update)
        testScheduler.runCurrent()

        val roots = manager.rootResources.value
        assertEquals(1, roots.size)
        assertEquals(resource1.name, roots[0].resourceName)
        assertEquals(1, resourceListener.created.size)
    }

    @Test
    fun `resource watcher receives the AppHost path`() = runTest {
        val appHostPath = Path.of("test/path/AppHost.csproj")
        val client = MockAspireResourceClient()
        val resourceClient = MutableStateFlow<AspireResourceClient?>(client)
        val manager = createResourceTreeManager(resourceClient, appHostPath)
        val expectedAppHostPath = AspireAppHostPath(appHostPath.toAbsolutePath().toString())

        testScheduler.runCurrent()

        assertEquals(listOf(expectedAppHostPath), client.watchedAppHostPaths)
    }

    @Test
    fun `upsert retains the resource data supplied by the watcher`() = runTest {
        val client = MockAspireResourceClient()
        val resourceClient = MutableStateFlow<AspireResourceClient?>(client)
        val manager = createResourceTreeManager(resourceClient)
        testScheduler.runCurrent()
        val resource = buildResource("api", "API")
        val appHostPath = AspireAppHostPath("another/path/AppHost.csproj")
        val data = resource.toAspireResourceData(appHostPath)
        val change = AspireResourceChange.Upsert(data)
        val changes = listOf(change)
        val update = AspireResourceUpdate.Changes(changes)

        client.resourceUpdates.emit(update)
        testScheduler.runCurrent()

        assertSame(data, manager.rootResources.value.single().data.value)
    }

    @Test
    fun `upsert updates existing resource state`() = runTest {
        val client = MockAspireResourceClient()
        val resourceClient = MutableStateFlow<AspireResourceClient?>(client)
        val manager = createResourceTreeManager(resourceClient)
        testScheduler.runCurrent()
        val resourceListener = connectListener()

        val resourceName = "res-1"
        val resourceDisplayName = "Resource 1"

        val resource1 = buildResource(resourceName, resourceDisplayName, state = "Starting")
        val update1 = buildUpsertUpdate(listOf(resource1))
        client.resourceUpdates.emit(update1)
        testScheduler.runCurrent()
        assertEquals(1, resourceListener.created.size)

        val updatedResource1 = buildResource(resourceName, resourceDisplayName, state = "Running")
        val update2 = buildUpsertUpdate(listOf(updatedResource1))
        client.resourceUpdates.emit(update2)
        testScheduler.runCurrent()
        assertEquals(1, resourceListener.updated.size)

        val roots = manager.rootResources.value
        assertEquals(1, roots.size)
        assertEquals(resourceName, roots[0].resourceName)
        assertEquals(resourceDisplayName, roots[0].displayName)
        assertEquals("Running", roots[0].data.value.state?.name)
    }

    @Test
    fun `delete removes resource from tree`() = runTest {
        val client = MockAspireResourceClient()
        val resourceClient = MutableStateFlow<AspireResourceClient?>(client)
        val manager = createResourceTreeManager(resourceClient)
        testScheduler.runCurrent()
        val resourceListener = connectListener()

        val resourceName = "res-1"
        val resourceDisplayName = "Resource 1"

        val resource = buildResource(resourceName, resourceDisplayName)
        val update1 = buildUpsertUpdate(listOf(resource))
        client.resourceUpdates.emit(update1)
        testScheduler.runCurrent()
        assertEquals(1, manager.rootResources.value.size)

        val deleteUpdate = buildDeleteUpdate(listOf(resource))
        client.resourceUpdates.emit(deleteUpdate)
        testScheduler.runCurrent()

        assertEquals(0, manager.rootResources.value.size)
        assertEquals(1, resourceListener.deleted.size)
    }

    @Test
    fun `delete of unknown resource is no-op`() = runTest {
        val client = MockAspireResourceClient()
        val resourceClient = MutableStateFlow<AspireResourceClient?>(client)
        val manager = createResourceTreeManager(resourceClient)
        testScheduler.runCurrent()
        val resourceListener = connectListener()

        val resource = buildResource("nonexistent", "Resource 1")
        val deleteUpdate = buildDeleteUpdate(listOf(resource))
        client.resourceUpdates.emit(deleteUpdate)
        testScheduler.runCurrent()

        assertEquals(0, manager.rootResources.value.size)
        assertEquals(0, resourceListener.deleted.size)
    }

    // endregion

    // region Hidden Resources

    @Test
    fun `hidden resource is retained with its hidden flag`() = runTest {
        val client = MockAspireResourceClient()
        val resourceClient = MutableStateFlow<AspireResourceClient?>(client)
        val manager = createResourceTreeManager(resourceClient)
        testScheduler.runCurrent()
        val resourceListener = connectListener()

        val resource = buildResource("res-1", "Resource 1", isHidden = true)
        val update1 = buildUpsertUpdate(listOf(resource))
        client.resourceUpdates.emit(update1)
        testScheduler.runCurrent()

        val storedResource = manager.rootResources.value.single()
        assertEquals(resource.name, storedResource.resourceName)
        assertTrue(storedResource.data.value.isHidden)
        assertEquals(listOf(resource.name), resourceListener.created)
    }

    @Test
    fun `resource with Hidden state is retained`() = runTest {
        val client = MockAspireResourceClient()
        val resourceClient = MutableStateFlow<AspireResourceClient?>(client)
        val manager = createResourceTreeManager(resourceClient)
        testScheduler.runCurrent()
        val resourceListener = connectListener()

        val resource = buildResource("res-1", "Resource 1", state = "Hidden")
        val update1 = buildUpsertUpdate(listOf(resource))
        client.resourceUpdates.emit(update1)
        testScheduler.runCurrent()

        val storedResource = manager.rootResources.value.single()
        assertEquals(ResourceState.Hidden, storedResource.data.value.state)
        assertEquals(listOf(resource.name), resourceListener.created)
    }

    @Test
    fun `existing resource becoming hidden is updated in place`() = runTest {
        val client = MockAspireResourceClient()
        val resourceClient = MutableStateFlow<AspireResourceClient?>(client)
        val manager = createResourceTreeManager(resourceClient)
        testScheduler.runCurrent()
        val resourceListener = connectListener()

        val resourceName = "res-1"
        val resourceDisplayName = "Resource 1"

        val resource = buildResource(resourceName, resourceDisplayName)
        val update1 = buildUpsertUpdate(listOf(resource))
        client.resourceUpdates.emit(update1)
        testScheduler.runCurrent()
        assertEquals(1, manager.rootResources.value.size)
        val storedResource = manager.rootResources.value.single()

        val hiddenResource = buildResource(resourceName, resourceDisplayName, isHidden = true)
        val update2 = buildUpsertUpdate(listOf(hiddenResource))
        client.resourceUpdates.emit(update2)
        testScheduler.runCurrent()

        assertEquals(1, manager.rootResources.value.size)
        assertEquals(storedResource, manager.rootResources.value.single())
        assertTrue(storedResource.data.value.isHidden)
        assertEquals(listOf(resourceName), resourceListener.updated)
        assertTrue(resourceListener.deleted.isEmpty())
    }

    // endregion

    // region Parent-Child Tree Management

    @Test
    fun `child arriving after parent attaches correctly`() = runTest {
        val client = MockAspireResourceClient()
        val resourceClient = MutableStateFlow<AspireResourceClient?>(client)
        val manager = createResourceTreeManager(resourceClient)
        testScheduler.runCurrent()

        val parentResourceName = "parent"
        val parentDisplayName = "Parent"
        val parentResource = buildResource(parentResourceName, parentDisplayName)
        val parentUpdate = buildUpsertUpdate(listOf(parentResource))
        client.resourceUpdates.emit(parentUpdate)
        testScheduler.runCurrent()

        val childResourceName = "child"
        val childResource = buildResource(childResourceName, "Child", parentDisplayName = parentDisplayName)
        val childUpdate = buildUpsertUpdate(listOf(childResource))
        client.resourceUpdates.emit(childUpdate)
        testScheduler.runCurrent()

        val roots = manager.rootResources.value
        assertEquals(1, roots.size)
        assertEquals(parentResourceName, roots[0].resourceName)

        val children = roots[0].childrenResources.value
        assertEquals(1, children.size)
        assertEquals(childResourceName, children[0].resourceName)
    }

    @Test
    fun `child arriving before parent is temporarily root then re-attached`() = runTest {
        val client = MockAspireResourceClient()
        val resourceClient = MutableStateFlow<AspireResourceClient?>(client)
        val manager = createResourceTreeManager(resourceClient)
        testScheduler.runCurrent()

        val parentDisplayName = "Parent"
        val childResourceName = "child"
        val childResource = buildResource(childResourceName, "Child", parentDisplayName = parentDisplayName)
        val childUpdate = buildUpsertUpdate(listOf(childResource))
        client.resourceUpdates.emit(childUpdate)
        testScheduler.runCurrent()

        // Child should be temporarily a root
        assertEquals(1, manager.rootResources.value.size)
        assertEquals(childResourceName, manager.rootResources.value[0].resourceName)

        // Now parent arrives
        val parentResourceName = "parent"
        val parentResource = buildResource(parentResourceName, parentDisplayName)
        val parentUpdate = buildUpsertUpdate(listOf(parentResource))
        client.resourceUpdates.emit(parentUpdate)
        testScheduler.runCurrent()

        val roots = manager.rootResources.value
        assertEquals(1, roots.size)
        assertEquals(parentResourceName, roots[0].resourceName)

        val children = roots[0].childrenResources.value
        assertEquals(1, children.size)
        assertEquals(childResourceName, children[0].resourceName)
    }

    @Test
    fun `removing parent re-promotes children to root`() = runTest {
        val client = MockAspireResourceClient()
        val resourceClient = MutableStateFlow<AspireResourceClient?>(client)
        val manager = createResourceTreeManager(resourceClient)
        testScheduler.runCurrent()

        val parentResourceName = "parent"
        val parentDisplayName = "Parent"
        val parentResource = buildResource(parentResourceName, parentDisplayName)
        val parentUpdate = buildUpsertUpdate(listOf(parentResource))
        client.resourceUpdates.emit(parentUpdate)
        testScheduler.runCurrent()

        val childResourceName = "child"
        val childResource = buildResource(childResourceName, "Child", parentDisplayName = parentDisplayName)
        val childUpdate = buildUpsertUpdate(listOf(childResource))
        client.resourceUpdates.emit(childUpdate)
        testScheduler.runCurrent()

        assertEquals(1, manager.rootResources.value.size)

        // Remove parent
        val deleteParentUpdate = buildDeleteUpdate(listOf(parentResource))
        client.resourceUpdates.emit(deleteParentUpdate)
        testScheduler.runCurrent()

        // Child should be promoted to root
        val roots = manager.rootResources.value
        assertEquals(1, roots.size)
        assertEquals(childResourceName, roots[0].resourceName)
    }

    @Test
    fun `multiple pending children attach when parent arrives`() = runTest {
        val client = MockAspireResourceClient()
        val resourceClient = MutableStateFlow<AspireResourceClient?>(client)
        val manager = createResourceTreeManager(resourceClient)
        testScheduler.runCurrent()

        val parentResourceName = "parent"
        val parentDisplayName = "Parent"

        val childResourceName1 = "child-1"
        val childResource1 = buildResource(childResourceName1, "Child 1", parentDisplayName = parentDisplayName)
        val childUpdate1 = buildUpsertUpdate(listOf(childResource1))
        client.resourceUpdates.emit(childUpdate1)
        testScheduler.runCurrent()

        val childResourceName2 = "child-2"
        val childResource2 = buildResource(childResourceName2, "Child 2", parentDisplayName = parentDisplayName)
        val childUpdate2 = buildUpsertUpdate(listOf(childResource2))
        client.resourceUpdates.emit(childUpdate2)
        testScheduler.runCurrent()

        // Both children should be temporary roots
        assertEquals(2, manager.rootResources.value.size)

        // Parent arrives
        val parentResource = buildResource(parentResourceName, parentDisplayName)
        val parentUpdate = buildUpsertUpdate(listOf(parentResource))
        client.resourceUpdates.emit(parentUpdate)
        testScheduler.runCurrent()

        val roots = manager.rootResources.value
        assertEquals(1, roots.size)
        assertEquals("parent", roots[0].resourceName)

        val children = roots[0].childrenResources.value
        assertEquals(2, children.size)
        assertTrue(children.any { it.resourceName == childResourceName1 })
        assertTrue(children.any { it.resourceName == childResourceName2 })
    }

    // endregion

    // region Clear All Resources

    @Test
    fun `removing the client removes all resources`() = runTest {
        val client = MockAspireResourceClient()
        val resourceClient = MutableStateFlow<AspireResourceClient?>(client)
        val manager = createResourceTreeManager(resourceClient)
        testScheduler.runCurrent()
        val resourceListener = connectListener()

        val resource1 = buildResource("res-1", "Resource 1")
        val resource2 = buildResource("res-2", "Resource 2")
        val initialData = buildInitialData(listOf(resource1, resource2))
        client.resourceUpdates.emit(initialData)
        testScheduler.runCurrent()

        assertEquals(2, manager.rootResources.value.size)

        resourceClient.value = null
        testScheduler.runCurrent()

        assertEquals(0, manager.rootResources.value.size)
        assertEquals(2, resourceListener.deleted.size)
    }

    @Test
    fun `second initial data replaces first`() = runTest {
        val client = MockAspireResourceClient()
        val resourceClient = MutableStateFlow<AspireResourceClient?>(client)
        val manager = createResourceTreeManager(resourceClient)
        testScheduler.runCurrent()

        val resourceName1 = "child-1"
        val resource1 = buildResource(resourceName1, "Resource 1")
        val initialData1 = buildInitialData(listOf(resource1))
        client.resourceUpdates.emit(initialData1)
        testScheduler.runCurrent()

        assertEquals(1, manager.rootResources.value.size)
        assertEquals(resourceName1, manager.rootResources.value[0].resourceName)

        val resourceName2 = "child-2"
        val resource2 = buildResource(resourceName2, "Resource 2")
        val initialData2 = buildInitialData(listOf(resource2))
        client.resourceUpdates.emit(initialData2)
        testScheduler.runCurrent()

        assertEquals(1, manager.rootResources.value.size)
        assertEquals(resourceName2, manager.rootResources.value[0].resourceName)
    }

    // endregion

    // region MessageBus Events

    @Test
    fun `resourceCreated event is published`() = runTest {
        val client = MockAspireResourceClient()
        val resourceClient = MutableStateFlow<AspireResourceClient?>(client)
        val manager = createResourceTreeManager(resourceClient)
        testScheduler.runCurrent()
        val resourceListener = connectListener()

        val resourceName = "res-1"
        val resource = buildResource(resourceName, "Resource 1")
        val update = buildUpsertUpdate(listOf(resource))
        client.resourceUpdates.emit(update)
        testScheduler.runCurrent()

        assertEquals(1, resourceListener.created.size)
        assertEquals(resourceName, resourceListener.created[0])
    }

    @Test
    fun `resourceUpdated event is published`() = runTest {
        val client = MockAspireResourceClient()
        val resourceClient = MutableStateFlow<AspireResourceClient?>(client)
        val manager = createResourceTreeManager(resourceClient)
        testScheduler.runCurrent()
        val resourceListener = connectListener()

        val resourceName = "res-1"
        val resourceDisplayName = "Resource 1"
        val resource = buildResource(resourceName, resourceDisplayName, state = "Starting")
        val update1 = buildUpsertUpdate(listOf(resource))
        client.resourceUpdates.emit(update1)
        testScheduler.runCurrent()

        val updatedResource = buildResource(resourceName, resourceDisplayName, state = "Running")
        val update2 = buildUpsertUpdate(listOf(updatedResource))
        client.resourceUpdates.emit(update2)
        testScheduler.runCurrent()

        assertEquals(1, resourceListener.updated.size)
        assertEquals(resourceName, resourceListener.updated[0])
    }

    @Test
    fun `resourceDeleted event is published`() = runTest {
        val client = MockAspireResourceClient()
        val resourceClient = MutableStateFlow<AspireResourceClient?>(client)
        val manager = createResourceTreeManager(resourceClient)
        testScheduler.runCurrent()
        val resourceListener = connectListener()

        val resourceName = "res-1"
        val resource = buildResource(resourceName, "Resource 1")
        val update = buildUpsertUpdate(listOf(resource))
        client.resourceUpdates.emit(update)
        testScheduler.runCurrent()

        val deleteUpdate = buildDeleteUpdate(listOf(resource))
        client.resourceUpdates.emit(deleteUpdate)
        testScheduler.runCurrent()

        assertEquals(1, resourceListener.deleted.size)
        assertEquals(resourceName, resourceListener.deleted[0])
    }

    // endregion

    // region Client Observation

    @Test
    fun `replacing the client clears old resources and watches the new client`() = runTest {
        val firstClient = MockAspireResourceClient()
        val secondClient = MockAspireResourceClient()
        val resourceClient = MutableStateFlow<AspireResourceClient?>(firstClient)
        val manager = createResourceTreeManager(resourceClient)
        testScheduler.runCurrent()
        val resourceListener = connectListener()
        val resource = buildResource("api", "API")
        val update = buildUpsertUpdate(listOf(resource))
        firstClient.resourceUpdates.emit(update)
        testScheduler.runCurrent()
        val oldResource = manager.rootResources.value.single()
        val oldResourceDisposable = Disposer.newCheckedDisposable(oldResource)

        resourceClient.value = secondClient
        testScheduler.runCurrent()

        assertTrue(manager.rootResources.value.isEmpty())
        assertTrue(oldResourceDisposable.isDisposed())
        assertEquals(listOf(resource.name), resourceListener.deleted)
        assertEquals(0, firstClient.resourceUpdates.subscriptionCount.value)
        assertEquals(1, secondClient.resourceUpdates.subscriptionCount.value)
        assertFalse(firstClient.isShutdown.value)
    }

    @Test
    fun `updates from a replaced client are ignored`() = runTest {
        val firstClient = MockAspireResourceClient()
        val secondClient = MockAspireResourceClient()
        val resourceClient = MutableStateFlow<AspireResourceClient?>(firstClient)
        val manager = createResourceTreeManager(resourceClient)
        testScheduler.runCurrent()
        val resource = buildResource("api", "API")
        val update = buildUpsertUpdate(listOf(resource))
        resourceClient.value = secondClient
        testScheduler.runCurrent()

        firstClient.resourceUpdates.emit(update)
        testScheduler.runCurrent()

        assertTrue(manager.rootResources.value.isEmpty())
    }

    @Test
    fun `updates from the new client create new resources`() = runTest {
        val firstClient = MockAspireResourceClient()
        val secondClient = MockAspireResourceClient()
        val resourceClient = MutableStateFlow<AspireResourceClient?>(firstClient)
        val manager = createResourceTreeManager(resourceClient)
        testScheduler.runCurrent()
        val resource = buildResource("api", "API")
        val update = buildUpsertUpdate(listOf(resource))
        firstClient.resourceUpdates.emit(update)
        testScheduler.runCurrent()
        val oldResource = manager.rootResources.value.single()
        resourceClient.value = secondClient
        testScheduler.runCurrent()

        secondClient.resourceUpdates.emit(update)
        testScheduler.runCurrent()

        val newResource = manager.rootResources.value.single()
        assertNotSame(oldResource, newResource)
        assertEquals(resource.name, newResource.resourceName)
    }

    @Test
    fun `resources use the supplied client for console logs and commands`() = runTest {
        val client = MockAspireResourceClient()
        val resourceClient = MutableStateFlow<AspireResourceClient?>(client)
        val manager = createResourceTreeManager(resourceClient)
        testScheduler.runCurrent()
        val resource = buildResource("api", "API")
        val update = buildUpsertUpdate(listOf(resource))
        client.resourceUpdates.emit(update)
        testScheduler.runCurrent()
        val storedResource = manager.rootResources.value.single()
        val expectedRequest = AspireResourceCommandRequest(resource.name, storedResource.data.value.originType, "restart")

        storedResource.executeCommand("restart")

        assertEquals(listOf(resource.name), client.watchedResourceNames)
        assertEquals(listOf(expectedRequest), client.commandRequests)
    }

    @Test
    fun `removing the client clears resources without shutting down the client`() = runTest {
        val client = MockAspireResourceClient()
        val resourceClient = MutableStateFlow<AspireResourceClient?>(client)
        val manager = createResourceTreeManager(resourceClient)
        testScheduler.runCurrent()
        val resourceListener = connectListener()
        val resource = buildResource("api", "API")
        val update = buildUpsertUpdate(listOf(resource))
        client.resourceUpdates.emit(update)
        testScheduler.runCurrent()
        val storedResource = manager.rootResources.value.single()
        val storedResourceDisposable = Disposer.newCheckedDisposable(storedResource)

        resourceClient.value = null
        testScheduler.runCurrent()

        assertTrue(manager.rootResources.value.isEmpty())
        assertTrue(storedResourceDisposable.isDisposed())
        assertEquals(listOf(resource.name), resourceListener.deleted)
        assertEquals(0, client.resourceUpdates.subscriptionCount.value)
        assertFalse(client.isShutdown.value)
    }

    @Test
    fun `awaiting observation shutdown waits for resource and console log subscriptions to end`() = runTest {
        val client = MockAspireResourceClient()
        val resourceClient = MutableStateFlow<AspireResourceClient?>(client)
        val manager = createResourceTreeManager(resourceClient)
        testScheduler.runCurrent()
        val resourceListener = connectListener()
        val resource = buildResource("api", "API")
        val update = buildUpsertUpdate(listOf(resource))
        client.resourceUpdates.emit(update)
        testScheduler.runCurrent()
        assertEquals(1, client.consoleLogSubscriptionCount)

        resourceClient.value = null
        manager.awaitResourceObservationStopped()

        assertTrue(manager.rootResources.value.isEmpty())
        assertEquals(listOf(resource.name), resourceListener.deleted)
        assertEquals(0, client.resourceUpdates.subscriptionCount.value)
        assertEquals(0, client.consoleLogSubscriptionCount)
        assertFalse(client.isShutdown.value)
    }

    @Test
    fun `cancelling the parent scope cancels resource watching and clears resources`() = runTest {
        val parentJob = Job(backgroundScope.coroutineContext[Job])
        val parentCs = CoroutineScope(backgroundScope.coroutineContext + parentJob)
        val client = MockAspireResourceClient()
        val resourceClient = MutableStateFlow<AspireResourceClient?>(client)
        val manager = createResourceTreeManager(resourceClient, parentCs = parentCs)
        testScheduler.runCurrent()
        val resourceListener = connectListener()
        val resource = buildResource("api", "API")
        val update = buildUpsertUpdate(listOf(resource))
        client.resourceUpdates.emit(update)
        testScheduler.runCurrent()
        val storedResource = manager.rootResources.value.single()
        val storedResourceDisposable = Disposer.newCheckedDisposable(storedResource)

        parentCs.cancel()
        testScheduler.runCurrent()

        assertTrue(manager.rootResources.value.isEmpty())
        assertTrue(storedResourceDisposable.isDisposed())
        assertEquals(listOf(resource.name), resourceListener.deleted)
        assertEquals(0, client.resourceUpdates.subscriptionCount.value)
        assertFalse(client.isShutdown.value)
    }

    // endregion

    // region Helpers

    private fun TestScope.createResourceTreeManager(
        resourceClient: StateFlow<AspireResourceClient?>,
        appHostPath: Path = Path.of("test/path/AppHost.csproj"),
        parentCs: CoroutineScope = backgroundScope,
    ): ResourceTreeManager = ResourceTreeManager(
        appHostPath,
        project,
        parentCs,
        testRootDisposable,
        resourceClient,
        uiDispatcher = StandardTestDispatcher(testScheduler),
    )

    private fun connectListener(): TestResourceListener {
        val resourceListener = TestResourceListener()
        project.messageBus.connect(testRootDisposable).subscribe(ResourceListener.TOPIC, resourceListener)
        return resourceListener
    }

    private fun buildResource(
        name: String,
        displayName: String,
        resourceType: String = "Project",
        state: String = "Running",
        isHidden: Boolean = false,
        parentDisplayName: String? = null,
    ): Resource {
        val builder = Resource.newBuilder()
            .setName(name)
            .setResourceType(resourceType)
            .setDisplayName(displayName)
            .setUid("uid-$name")
            .setState(state)
            .setIsHidden(isHidden)

        if (parentDisplayName != null) {
            val relationship = com.jetbrains.aspire.generated.dashboard.ResourceRelationship.newBuilder()
                .setResourceName(parentDisplayName)
                .setType("parent")
                .build()
            builder.addRelationships(relationship)
        }

        return builder.build()
    }

    private fun buildInitialData(resources: List<Resource>): AspireResourceUpdate {
        val appHostPath = AspireAppHostPath("test/path/AppHost.csproj")
        val data = resources.map { it.toAspireResourceData(appHostPath) }
        return AspireResourceUpdate.InitialData(data)
    }

    private fun buildUpsertUpdate(resources: List<Resource>): AspireResourceUpdate {
        val appHostPath = AspireAppHostPath("test/path/AppHost.csproj")
        val changes = resources.map {
            val data = it.toAspireResourceData(appHostPath)
            AspireResourceChange.Upsert(data)
        }
        return AspireResourceUpdate.Changes(changes)
    }

    private fun buildDeleteUpdate(resources: List<Resource>): AspireResourceUpdate {
        val changes = resources.map { AspireResourceChange.Delete(it.name) }
        return AspireResourceUpdate.Changes(changes)
    }

    // endregion

    private class TestResourceListener : ResourceListener {
        val created = mutableListOf<String>()
        val updated = mutableListOf<String>()
        val deleted = mutableListOf<String>()

        override fun resourceCreated(resource: AspireResource) {
            created.add(resource.resourceName)
        }

        override fun resourceUpdated(resource: AspireResource) {
            updated.add(resource.resourceName)
        }

        override fun resourceDeleted(resource: AspireResource) {
            deleted.add(resource.resourceName)
        }
    }
}
