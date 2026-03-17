package com.jetbrains.aspire.worker

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.replaceService
import com.jetbrains.aspire.dashboard.ResourceListener
import com.jetbrains.aspire.generated.dashboard.*
import com.jetbrains.aspire.generated.dashboard.WatchResourcesUpdate.*
import com.jetbrains.aspire.worker.AspireAppHost.AppHostEnvironment
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.nio.file.Path

@RunWith(JUnit4::class)
class ResourceTreeManagerTest {
    companion object {
        @ClassRule
        @JvmField
        val appRule = ApplicationRule()
    }

    @Rule
    @JvmField
    val disposableRule = DisposableRule()

    private val project get() = ProjectManager.getInstance().defaultProject
    private val testRootDisposable get() = disposableRule.disposable

    private lateinit var mockFactory: MockAspireDashboardClientFactory

    @Before
    fun setUpService() {
        mockFactory = MockAspireDashboardClientFactory()
        ApplicationManager.getApplication().replaceService(
            AspireDashboardClientFactory::class.java,
            mockFactory,
            testRootDisposable
        )
    }

    // region Resource CRUD

    @Test
    fun `initial data creates root resources`() = runTest {
        val manager = createResourceTreeManager()
        val (job, client) = startDashboardClient(manager)
        val resourceListener = connectListener()

        val resource1 = buildResource("res-1", "Resource 1")
        val resource2 = buildResource("res-2", "Resource 2")
        val initialData = buildInitialData(listOf(resource1, resource2))
        client.resourceUpdates.emit(initialData)
        testScheduler.advanceUntilIdle()

        val roots = manager.rootResources.value
        assertEquals(2, roots.size)
        assertEquals(resource1.name, roots[0].resourceName)
        assertEquals(resource2.name, roots[1].resourceName)
        assertEquals(2, resourceListener.created.size)

        job.cancel()
    }

    @Test
    fun `upsert creates new resource`() = runTest {
        val manager = createResourceTreeManager()
        val (job, client) = startDashboardClient(manager)
        val resourceListener = connectListener()

        val resource1 = buildResource("res-1", "Resource 1")
        val update = buildUpsertUpdate(listOf(resource1))
        client.resourceUpdates.emit(update)
        testScheduler.advanceUntilIdle()

        val roots = manager.rootResources.value
        assertEquals(1, roots.size)
        assertEquals(resource1.name, roots[0].resourceName)
        assertEquals(1, resourceListener.created.size)

        job.cancel()
    }

    @Test
    fun `upsert updates existing resource state`() = runTest {
        val manager = createResourceTreeManager()
        val (job, client) = startDashboardClient(manager)
        val resourceListener = connectListener()

        val resourceName = "res-1"
        val resourceDisplayName = "Resource 1"

        val resource1 = buildResource(resourceName, resourceDisplayName, state = "Starting")
        val update1 = buildUpsertUpdate(listOf(resource1))
        client.resourceUpdates.emit(update1)
        testScheduler.advanceUntilIdle()
        assertEquals(1, resourceListener.created.size)

        val updatedResource1 = buildResource(resourceName, resourceDisplayName, state = "Running")
        val update2 = buildUpsertUpdate(listOf(updatedResource1))
        client.resourceUpdates.emit(update2)
        testScheduler.advanceUntilIdle()
        assertEquals(1, resourceListener.updated.size)

        val roots = manager.rootResources.value
        assertEquals(1, roots.size)
        assertEquals(resourceName, roots[0].resourceName)
        assertEquals(resourceDisplayName, roots[0].displayName)
        assertEquals("Running", roots[0].resourceState.value.state?.name)

        job.cancel()
    }

    @Test
    fun `delete removes resource from tree`() = runTest {
        val manager = createResourceTreeManager()
        val (job, client) = startDashboardClient(manager)
        val resourceListener = connectListener()

        val resourceName = "res-1"
        val resourceDisplayName = "Resource 1"

        val resource = buildResource(resourceName, resourceDisplayName)
        val update1 = buildUpsertUpdate(listOf(resource))
        client.resourceUpdates.emit(update1)
        testScheduler.advanceUntilIdle()
        assertEquals(1, manager.rootResources.value.size)

        val deleteUpdate = buildDeleteUpdate(listOf(resource))
        client.resourceUpdates.emit(deleteUpdate)
        testScheduler.advanceUntilIdle()

        assertEquals(0, manager.rootResources.value.size)
        assertEquals(1, resourceListener.deleted.size)

        job.cancel()
    }

    @Test
    fun `delete of unknown resource is no-op`() = runTest {
        val manager = createResourceTreeManager()
        val (job, client) = startDashboardClient(manager)
        val resourceListener = connectListener()

        val resource = buildResource("nonexistent", "Resource 1")
        val deleteUpdate = buildDeleteUpdate(listOf(resource))
        client.resourceUpdates.emit(deleteUpdate)
        testScheduler.advanceUntilIdle()

        assertEquals(0, manager.rootResources.value.size)
        assertEquals(0, resourceListener.deleted.size)

        job.cancel()
    }

    // endregion

    // region Hidden Resources

    @Test
    fun `hidden resource is not created`() = runTest {
        val manager = createResourceTreeManager()
        val (job, client) = startDashboardClient(manager)
        val resourceListener = connectListener()

        val resource = buildResource("res-1", "Resource 1", isHidden = true)
        val update1 = buildUpsertUpdate(listOf(resource))
        client.resourceUpdates.emit(update1)
        testScheduler.advanceUntilIdle()

        assertEquals(0, manager.rootResources.value.size)
        assertEquals(0, resourceListener.created.size)

        job.cancel()
    }

    @Test
    fun `resource with Hidden state is not created`() = runTest {
        val manager = createResourceTreeManager()
        val (job, client) = startDashboardClient(manager)
        val resourceListener = connectListener()

        val resource = buildResource("res-1", "Resource 1", state = "Hidden")
        val update1 = buildUpsertUpdate(listOf(resource))
        client.resourceUpdates.emit(update1)
        testScheduler.advanceUntilIdle()

        assertEquals(0, manager.rootResources.value.size)
        assertEquals(0, resourceListener.created.size)

        job.cancel()
    }

    @Test
    fun `existing resource becoming hidden is removed`() = runTest {
        val manager = createResourceTreeManager()
        val (job, client) = startDashboardClient(manager)
        val resourceListener = connectListener()

        val resourceName = "res-1"
        val resourceDisplayName = "Resource 1"

        val resource = buildResource(resourceName, resourceDisplayName)
        val update1 = buildUpsertUpdate(listOf(resource))
        client.resourceUpdates.emit(update1)
        testScheduler.advanceUntilIdle()
        assertEquals(1, manager.rootResources.value.size)

        val hiddenResource = buildResource(resourceName, resourceDisplayName, isHidden = true)
        val update2 = buildUpsertUpdate(listOf(hiddenResource))
        client.resourceUpdates.emit(update2)
        testScheduler.advanceUntilIdle()

        assertEquals(0, manager.rootResources.value.size)
        assertEquals(1, resourceListener.deleted.size)

        job.cancel()
    }

    // endregion

    // region Parent-Child Tree Management

    @Test
    fun `child arriving after parent attaches correctly`() = runTest {
        val manager = createResourceTreeManager()
        val (job, client) = startDashboardClient(manager)

        val parentResourceName = "parent"
        val parentDisplayName = "Parent"
        val parentResource = buildResource(parentResourceName, parentDisplayName)
        val parentUpdate = buildUpsertUpdate(listOf(parentResource))
        client.resourceUpdates.emit(parentUpdate)
        testScheduler.advanceUntilIdle()

        val childResourceName = "child"
        val childResource = buildResource(childResourceName, "Child", parentDisplayName = parentDisplayName)
        val childUpdate = buildUpsertUpdate(listOf(childResource))
        client.resourceUpdates.emit(childUpdate)
        testScheduler.advanceUntilIdle()

        val roots = manager.rootResources.value
        assertEquals(1, roots.size)
        assertEquals(parentResourceName, roots[0].resourceName)

        val children = roots[0].childrenResources.value
        assertEquals(1, children.size)
        assertEquals(childResourceName, children[0].resourceName)

        job.cancel()
    }

    @Test
    fun `child arriving before parent is temporarily root then re-attached`() = runTest {
        val manager = createResourceTreeManager()
        val (job, client) = startDashboardClient(manager)

        val parentDisplayName = "Parent"
        val childResourceName = "child"
        val childResource = buildResource(childResourceName, "Child", parentDisplayName = parentDisplayName)
        val childUpdate = buildUpsertUpdate(listOf(childResource))
        client.resourceUpdates.emit(childUpdate)
        testScheduler.advanceUntilIdle()

        // Child should be temporarily a root
        assertEquals(1, manager.rootResources.value.size)
        assertEquals(childResourceName, manager.rootResources.value[0].resourceName)

        // Now parent arrives
        val parentResourceName = "parent"
        val parentResource = buildResource(parentResourceName, parentDisplayName)
        val parentUpdate = buildUpsertUpdate(listOf(parentResource))
        client.resourceUpdates.emit(parentUpdate)
        testScheduler.advanceUntilIdle()

        val roots = manager.rootResources.value
        assertEquals(1, roots.size)
        assertEquals(parentResourceName, roots[0].resourceName)

        val children = roots[0].childrenResources.value
        assertEquals(1, children.size)
        assertEquals(childResourceName, children[0].resourceName)

        job.cancel()
    }

    @Test
    fun `removing parent re-promotes children to root`() = runTest {
        val manager = createResourceTreeManager()
        val (job, client) = startDashboardClient(manager)

        val parentResourceName = "parent"
        val parentDisplayName = "Parent"
        val parentResource = buildResource(parentResourceName, parentDisplayName)
        val parentUpdate = buildUpsertUpdate(listOf(parentResource))
        client.resourceUpdates.emit(parentUpdate)
        testScheduler.advanceUntilIdle()

        val childResourceName = "child"
        val childResource = buildResource(childResourceName, "Child", parentDisplayName = parentDisplayName)
        val childUpdate = buildUpsertUpdate(listOf(childResource))
        client.resourceUpdates.emit(childUpdate)
        testScheduler.advanceUntilIdle()

        assertEquals(1, manager.rootResources.value.size)

        // Remove parent
        val deleteParentUpdate = buildDeleteUpdate(listOf(parentResource))
        client.resourceUpdates.emit(deleteParentUpdate)
        testScheduler.advanceUntilIdle()

        // Child should be promoted to root
        val roots = manager.rootResources.value
        assertEquals(1, roots.size)
        assertEquals(childResourceName, roots[0].resourceName)

        job.cancel()
    }

    @Test
    fun `multiple pending children attach when parent arrives`() = runTest {
        val manager = createResourceTreeManager()
        val (job, client) = startDashboardClient(manager)

        val parentResourceName = "parent"
        val parentDisplayName = "Parent"

        val childResourceName1 = "child-1"
        val childResource1 = buildResource(childResourceName1, "Child 1", parentDisplayName = parentDisplayName)
        val childUpdate1 = buildUpsertUpdate(listOf(childResource1))
        client.resourceUpdates.emit(childUpdate1)
        testScheduler.advanceUntilIdle()

        val childResourceName2 = "child-2"
        val childResource2 = buildResource(childResourceName2, "Child 2", parentDisplayName = parentDisplayName)
        val childUpdate2 = buildUpsertUpdate(listOf(childResource2))
        client.resourceUpdates.emit(childUpdate2)
        testScheduler.advanceUntilIdle()

        // Both children should be temporary roots
        assertEquals(2, manager.rootResources.value.size)

        // Parent arrives
        val parentResource = buildResource(parentResourceName, parentDisplayName)
        val parentUpdate = buildUpsertUpdate(listOf(parentResource))
        client.resourceUpdates.emit(parentUpdate)
        testScheduler.advanceUntilIdle()

        val roots = manager.rootResources.value
        assertEquals(1, roots.size)
        assertEquals("parent", roots[0].resourceName)

        val children = roots[0].childrenResources.value
        assertEquals(2, children.size)
        assertTrue(children.any { it.resourceName == childResourceName1 })
        assertTrue(children.any { it.resourceName == childResourceName2 })

        job.cancel()
    }

    // endregion

    // region Clear All Resources

    @Test
    fun `clearAllResources removes everything`() = runTest {
        val manager = createResourceTreeManager()
        val (job, client) = startDashboardClient(manager)
        val resourceListener = connectListener()

        val resource1 = buildResource("res-1", "Resource 1")
        val resource2 = buildResource("res-2", "Resource 2")
        val initialData = buildInitialData(listOf(resource1, resource2))
        client.resourceUpdates.emit(initialData)
        testScheduler.advanceUntilIdle()

        assertEquals(2, manager.rootResources.value.size)

        manager.clearAllResources()

        assertEquals(0, manager.rootResources.value.size)
        assertEquals(2, resourceListener.deleted.size)

        job.cancel()
    }

    @Test
    fun `second initial data replaces first`() = runTest {
        val manager = createResourceTreeManager()
        val (job, client) = startDashboardClient(manager)

        val resourceName1 = "child-1"
        val resource1 = buildResource(resourceName1, "Resource 1")
        val initialData1 = buildInitialData(listOf(resource1))
        client.resourceUpdates.emit(initialData1)
        testScheduler.advanceUntilIdle()

        assertEquals(1, manager.rootResources.value.size)
        assertEquals(resourceName1, manager.rootResources.value[0].resourceName)

        val resourceName2 = "child-2"
        val resource2 = buildResource(resourceName2, "Resource 2")
        val initialData2 = buildInitialData(listOf(resource2))
        client.resourceUpdates.emit(initialData2)
        testScheduler.advanceUntilIdle()

        assertEquals(1, manager.rootResources.value.size)
        assertEquals(resourceName2, manager.rootResources.value[0].resourceName)

        job.cancel()
    }

    // endregion

    // region MessageBus Events

    @Test
    fun `resourceCreated event is published`() = runTest {
        val manager = createResourceTreeManager()
        val (job, client) = startDashboardClient(manager)
        val resourceListener = connectListener()

        val resourceName = "res-1"
        val resource = buildResource(resourceName, "Resource 1")
        val update = buildUpsertUpdate(listOf(resource))
        client.resourceUpdates.emit(update)
        testScheduler.advanceUntilIdle()

        assertEquals(1, resourceListener.created.size)
        assertEquals(resourceName, resourceListener.created[0])

        job.cancel()
    }

    @Test
    fun `resourceUpdated event is published`() = runTest {
        val manager = createResourceTreeManager()
        val (job, client) = startDashboardClient(manager)
        val resourceListener = connectListener()

        val resourceName = "res-1"
        val resourceDisplayName = "Resource 1"
        val resource = buildResource(resourceName, resourceDisplayName, state = "Starting")
        val update1 = buildUpsertUpdate(listOf(resource))
        client.resourceUpdates.emit(update1)
        testScheduler.advanceUntilIdle()

        val updatedResource = buildResource(resourceName, resourceDisplayName, state = "Running")
        val update2 = buildUpsertUpdate(listOf(updatedResource))
        client.resourceUpdates.emit(update2)
        testScheduler.advanceUntilIdle()

        assertEquals(1, resourceListener.updated.size)
        assertEquals(resourceName, resourceListener.updated[0])

        job.cancel()
    }

    @Test
    fun `resourceDeleted event is published`() = runTest {
        val manager = createResourceTreeManager()
        val (job, client) = startDashboardClient(manager)
        val resourceListener = connectListener()

        val resourceName = "res-1"
        val resource = buildResource(resourceName, "Resource 1")
        val update = buildUpsertUpdate(listOf(resource))
        client.resourceUpdates.emit(update)
        testScheduler.advanceUntilIdle()

        val deleteUpdate = buildDeleteUpdate(listOf(resource))
        client.resourceUpdates.emit(deleteUpdate)
        testScheduler.advanceUntilIdle()

        assertEquals(1, resourceListener.deleted.size)
        assertEquals(resourceName, resourceListener.deleted[0])

        job.cancel()
    }

    // endregion

    // region Helpers

    private fun TestScope.createResourceTreeManager(): ResourceTreeManager = ResourceTreeManager(
        Path.of("test/path/AppHost.csproj"),
        project,
        this,
        testRootDisposable
    )

    private fun TestScope.startDashboardClient(treeManager: ResourceTreeManager): Pair<Job, MockAspireDashboardClientApi> {
        val environment = AppHostEnvironment(
            "http://localhost:18888",
            "test-key",
            null,
            null
        )
        val job = with(treeManager) { startDashboardClient(environment) }

        // Advance scheduler to start the collector coroutine so it's ready to receive emissions
        testScheduler.advanceUntilIdle()

        val client = requireNotNull(mockFactory.lastClient)

        return requireNotNull(job) to client
    }

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
            builder.addRelationships(
                com.jetbrains.aspire.generated.dashboard.ResourceRelationship.newBuilder()
                    .setResourceName(parentDisplayName)
                    .setType("parent")
            )
        }

        return builder.build()
    }

    private fun buildInitialData(resources: List<Resource>): WatchResourcesUpdate {
        val initialResourceDataBuilder = InitialResourceData.newBuilder()
        resources.forEach { initialResourceDataBuilder.addResources(it) }

        return newBuilder()
            .setInitialData(initialResourceDataBuilder)
            .build()
    }

    private fun buildUpsertUpdate(resources: List<Resource>): WatchResourcesUpdate {
        val watchResourcesChangesBuilder = WatchResourcesChanges.newBuilder()

        resources.forEach {
            val watchResourcesChangeBuilder = WatchResourcesChange.newBuilder()
                .setUpsert(it)
            watchResourcesChangesBuilder.addValue(watchResourcesChangeBuilder)
        }

        return newBuilder()
            .setChanges(watchResourcesChangesBuilder)
            .build()
    }

    private fun buildDeleteUpdate(resources: List<Resource>): WatchResourcesUpdate {
        val watchResourcesChangesBuilder = WatchResourcesChanges.newBuilder()

        resources.forEach {
            val resourceDeletion = ResourceDeletion.newBuilder()
                .setResourceName(it.name)
                .setResourceType(it.resourceType)
            val watchResourcesChangeBuilder = WatchResourcesChange.newBuilder()
                .setDelete(resourceDeletion)
            watchResourcesChangesBuilder.addValue(watchResourcesChangeBuilder)
        }

        return newBuilder()
            .setChanges(watchResourcesChangesBuilder)
            .build()
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
