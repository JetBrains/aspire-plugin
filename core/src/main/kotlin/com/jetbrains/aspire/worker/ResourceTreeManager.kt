@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.worker

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.jetbrains.aspire.dashboard.ResourceListener
import com.jetbrains.aspire.generated.dashboard.Resource
import com.jetbrains.aspire.generated.dashboard.ResourceDeletion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds

/**
 * Manages the resource tree for an Aspire AppHost.
 *
 * Handles creating, updating, removing, and attaching resources,
 * as well as tracking pending parent-child relationships. Also manages the
 * dashboard client lifecycle including retry logic and resource watching.
 *
 * Parent-child relationships are immutable: a resource's parent cannot change after creation,
 * though resources may arrive out of order (child before parent). Pending children are
 * tracked and re-attached when the parent appears.
 *
 * All resource mutation methods are called exclusively from the gRPC collection coroutine
 * in [startDashboardClient], so they are inherently single-threaded and do not require
 * additional synchronization. Plain [HashMap] is used instead of [java.util.concurrent.ConcurrentHashMap].
 */
internal class ResourceTreeManager(
    private val mainFilePath: Path,
    private val project: Project,
    private val parentCs: CoroutineScope,
    private val parentDisposable: Disposable,
) {
    companion object {
        private val LOG = logger<ResourceTreeManager>()
    }

    var dashboardClient: AspireDashboardClientApi? = null
        private set

    private val resources = HashMap<String, AspireResource>()
    private val resourcesByDisplayName = HashMap<String, AspireResource>()
    private val pendingChildren = HashMap<String, MutableSet<String>>()

    private val _rootResources = MutableStateFlow<List<AspireResource>>(emptyList())
    val rootResources: StateFlow<List<AspireResource>> = _rootResources.asStateFlow()

    fun CoroutineScope.startDashboardClient(environment: AspireAppHost.AppHostEnvironment): Job? {
        val endpointUrl = environment.resourceServiceEndpointUrl ?: return null

        LOG.trace { "Initializing gRPC dashboard client for $mainFilePath" }

        val clientFactory = service<AspireDashboardClientFactory>()
        val client = clientFactory.create(endpointUrl, environment.resourceServiceApiKey)
        dashboardClient = client
        return launch {
            try {
                client.watchResources()
                    .retryWhen { cause, attempt ->
                        if (cause is CancellationException) {
                            false
                        } else {
                            val retryDelay = (500L * (1 shl attempt.coerceAtMost(6).toInt())).coerceAtMost(30_000L)
                            LOG.trace { "gRPC dashboard connection failed for $mainFilePath, retrying in ${retryDelay}ms (attempt ${attempt + 1}): ${cause.message}" }
                            delay(retryDelay.milliseconds)
                            true
                        }
                    }
                    .collect { update ->
                        when {
                            update.hasInitialData() -> handleInitialData(update.initialData)
                            update.hasChanges() -> handleChanges(update.changes)
                        }
                    }
            } finally {
                withContext(NonCancellable) {
                    clearAllResources()
                }
                dashboardClient = null
                client.shutdown()
            }
        }
    }

    private suspend fun handleInitialData(data: com.jetbrains.aspire.generated.dashboard.InitialResourceData) {
        clearAllResources()

        for (resource in data.resourcesList) {
            upsertGrpcResource(resource)
        }
    }

    private suspend fun handleChanges(changes: com.jetbrains.aspire.generated.dashboard.WatchResourcesChanges) {
        for (change in changes.valueList) {
            when {
                change.hasUpsert() -> upsertGrpcResource(change.upsert)
                change.hasDelete() -> deleteGrpcResource(change.delete)
            }
        }
    }

    private suspend fun upsertGrpcResource(grpcResource: Resource) {
        val data = grpcResource.toAspireResourceData()
        val existing = resources[data.name]

        if (data.isHidden || data.state == ResourceState.Hidden) {
            if (existing != null) {
                removeResource(existing)
            }
            return
        }

        if (existing == null) {
            val client = dashboardClient ?: return
            val resource = AspireResource(
                data.name,
                data,
                project,
                parentCs,
                client
            )
            createResource(resource)
        } else {
            updateResource(existing, data)
        }
    }

    private suspend fun deleteGrpcResource(resourceDeletion: ResourceDeletion) {
        val resourceToRemove = resources[resourceDeletion.resourceName] ?: return
        removeResource(resourceToRemove)
    }

    private suspend fun createResource(resource: AspireResource) {
        if (resources.containsKey(resource.resourceName)) return

        resources[resource.resourceName] = resource
        resourcesByDisplayName[resource.displayName] = resource

        Disposer.register(parentDisposable, resource)
        attachResource(resource)

        processPendingResources(resource.displayName)

        withContext(Dispatchers.EDT) {
            project.messageBus.syncPublisher(ResourceListener.TOPIC).resourceCreated(resource)
        }
    }

    private suspend fun updateResource(resource: AspireResource, data: AspireResourceData) {
        resource.update(data)

        withContext(Dispatchers.EDT) {
            project.messageBus.syncPublisher(ResourceListener.TOPIC).resourceUpdated(resource)
        }
    }

    private suspend fun removeResource(resource: AspireResource) {
        if (!resources.containsKey(resource.resourceName)) return

        resources.remove(resource.resourceName)
        resourcesByDisplayName.remove(resource.displayName)

        detachFromParent(resource)

        for (childResource in resource.childrenResources.value.toList()) {
            resource.removeChildResource(childResource)
            attachResource(childResource)
        }

        Disposer.dispose(resource)

        withContext(Dispatchers.EDT) {
            project.messageBus.syncPublisher(ResourceListener.TOPIC).resourceDeleted(resource)
        }
    }

    /**
     * Attaches a resource to its parent based on [AspireResource.parentDisplayName].
     *
     * - If the resource has no parent, it becomes a root resource.
     * - If the parent exists, the resource is added as its child and removed from roots/pending.
     * - If the parent is not yet known, the resource is temporarily placed as a root and registered
     *   in [pendingChildren] so it can be re-attached when the parent appears.
     */
    private fun attachResource(resource: AspireResource) {
        val parentDisplayName = resource.parentDisplayName
        if (parentDisplayName == null) {
            addRootResource(resource)
            return
        }

        val parentResource = findResourceByDisplayName(parentDisplayName)
        if (parentResource == null) {
            addRootResource(resource)
            addPendingChild(parentDisplayName, resource.resourceName)
            return
        }

        removeRootResource(resource)
        removePendingChild(parentDisplayName, resource.resourceName)
        parentResource.addChildResource(resource)
    }

    /**
     * Detaches a resource from its parent (or from roots) and pending list.
     */
    private fun detachFromParent(resource: AspireResource) {
        val parentDisplayName = resource.parentDisplayName
        if (parentDisplayName == null) {
            removeRootResource(resource)
            return
        }

        val parentResource = findResourceByDisplayName(parentDisplayName)
        if (parentResource == null) {
            removeRootResource(resource)
            removePendingChild(parentDisplayName, resource.resourceName)
            return
        }

        parentResource.removeChildResource(resource)
    }

    private fun addRootResource(resource: AspireResource) {
        _rootResources.update { current ->
            if (resource in current) current
            else current + resource
        }
    }

    private fun removeRootResource(resource: AspireResource) {
        _rootResources.update { it - resource }
    }

    private fun addPendingChild(parentDisplayName: String, childResourceName: String) {
        pendingChildren
            .getOrPut(parentDisplayName) { mutableSetOf() }
            .add(childResourceName)
    }

    private fun removePendingChild(parentDisplayName: String, childResourceName: String) {
        val pending = pendingChildren[parentDisplayName] ?: return
        pending.remove(childResourceName)
        if (pending.isEmpty()) {
            pendingChildren.remove(parentDisplayName)
        }
    }

    private fun processPendingResources(parentDisplayName: String) {
        val pending = pendingChildren.remove(parentDisplayName) ?: return

        for (childResourceName in pending) {
            val childResource = resources[childResourceName] ?: continue
            if (childResource.parentDisplayName == parentDisplayName) {
                attachResource(childResource)
            }
        }
    }

    suspend fun clearAllResources() {
        for (resource in resources.values.toList()) {
            removeResource(resource)
        }
    }

    private fun findResourceByDisplayName(displayName: String): AspireResource? =
        resourcesByDisplayName[displayName]
}
