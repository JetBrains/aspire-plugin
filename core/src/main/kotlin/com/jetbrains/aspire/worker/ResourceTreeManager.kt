@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.worker

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.jetbrains.aspire.resources.AspireResourceChange
import com.jetbrains.aspire.resources.AspireResourceClient
import com.jetbrains.aspire.resources.AspireResourceUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds

/**
 * Manages the resource tree for an Aspire AppHost.
 *
 * Handles creating, updating, removing, and attaching resources,
 * as well as tracking pending parent-child relationships. Observes the client supplied by
 * [AspireAppHost] in its own coroutine scope, with retry logic for resource watching.
 * Replacing or removing the active client cancels resource watching and clears the tree.
 * The AppHost owns the client's lifecycle.
 *
 * Parent-child relationships are immutable: a resource's parent cannot change after creation,
 * though resources may arrive out of order (child before parent). Pending children are
 * tracked and re-attached when the parent appears.
 *
 * All resource mutation methods are called exclusively from the resource collection coroutine,
 * so they are inherently single-threaded and do not require additional synchronization.
 * Plain [HashMap] is used instead of [java.util.concurrent.ConcurrentHashMap].
 */
@ApiStatus.Internal
class ResourceTreeManager(
    private val appHostFile: Path,
    private val project: Project,
    parentCs: CoroutineScope,
    private val parentDisposable: Disposable,
    private val resourceClient: StateFlow<AspireResourceClient?>,
    private val uiDispatcher: CoroutineContext = Dispatchers.EDT,
) {
    companion object {
        private val LOG = logger<ResourceTreeManager>()
    }

    private val cs = parentCs.childScope("Aspire Resource Tree")
    private val resourceObservationMutex = Mutex()

    private val resources = HashMap<String, AspireResource>()
    private val resourcesByDisplayName = HashMap<String, AspireResource>()
    private val pendingChildren = HashMap<String, MutableSet<String>>()

    private val _rootResources = MutableStateFlow<List<AspireResource>>(emptyList())
    val rootResources: StateFlow<List<AspireResource>> = _rootResources.asStateFlow()

    init {
        cs.launch {
            resourceClient.collectLatest { client ->
                if (client == null) return@collectLatest

                resourceObservationMutex.withLock {
                    if (resourceClient.value !== client) return@withLock

                    coroutineScope {
                        val resourceCs = this
                        try {
                            client.watchResources(appHostFile.toAspireAppHostPath())
                                .retryWhen { cause, attempt ->
                                    if (cause is CancellationException) {
                                        false
                                    } else {
                                        val retryDelay = (500L * (1 shl attempt.coerceAtMost(6).toInt())).coerceAtMost(30_000L)
                                        LOG.trace { "gRPC dashboard connection failed for $appHostFile, retrying in ${retryDelay}ms (attempt ${attempt + 1}): ${cause.message}" }
                                        delay(retryDelay.milliseconds)
                                        true
                                    }
                                }
                                .collect { update ->
                                    when (update) {
                                        is AspireResourceUpdate.InitialData -> handleInitialData(update.resources, client, resourceCs)
                                        is AspireResourceUpdate.Changes -> handleChanges(update.changes, client, resourceCs)
                                    }
                                }
                        } finally {
                            withContext(NonCancellable) {
                                clearAllResources()
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Waits for resource watching, console log subscriptions, and tree cleanup to finish.
     * The owner must remove the client from [resourceClient] before awaiting its release.
     */
    suspend fun awaitResourceObservationStopped() {
        check(resourceClient.value == null) { "Remove the resource client before awaiting observation shutdown" }
        resourceObservationMutex.withLock { }
    }

    private suspend fun handleInitialData(
        data: List<AspireResourceData>,
        client: AspireResourceClient,
        resourceCs: CoroutineScope,
    ) {
        clearAllResources()

        for (resource in data) {
            upsertResource(resource, client, resourceCs)
        }
    }

    private suspend fun handleChanges(
        changes: List<AspireResourceChange>,
        client: AspireResourceClient,
        resourceCs: CoroutineScope,
    ) {
        for (change in changes) {
            when (change) {
                is AspireResourceChange.Upsert -> upsertResource(change.data, client, resourceCs)
                is AspireResourceChange.Delete -> deleteResource(change.resourceName)
            }
        }
    }

    private suspend fun upsertResource(data: AspireResourceData, client: AspireResourceClient, resourceCs: CoroutineScope) {
        val existing = resources[data.name]

        if (existing == null) {
            val resource = AspireResource(
                data.name,
                data,
                resourceCs,
                resourceLogWatcher = client,
                resourceCommandExecutor = client,
            )
            createResource(resource)
        } else {
            updateResource(existing, data)
        }
    }

    private suspend fun deleteResource(resourceName: String) {
        val resourceToRemove = resources[resourceName] ?: return
        removeResource(resourceToRemove)
    }

    private suspend fun createResource(resource: AspireResource) {
        if (resources.containsKey(resource.resourceName)) return

        Disposer.register(parentDisposable, resource)

        resources[resource.resourceName] = resource
        resourcesByDisplayName[resource.displayName] = resource

        attachResource(resource)

        processPendingResources(resource.displayName)

        withContext(uiDispatcher) {
            project.messageBus.syncPublisher(ResourceListener.TOPIC).resourceCreated(resource)
        }
    }

    private suspend fun updateResource(resource: AspireResource, data: AspireResourceData) {
        resource.update(data)

        withContext(uiDispatcher) {
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

        withContext(uiDispatcher) {
            project.messageBus.syncPublisher(ResourceListener.TOPIC).resourceDeleted(resource)
        }

        Disposer.dispose(resource)
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

    private suspend fun clearAllResources() {
        for (resource in resources.values.toList()) {
            removeResource(resource)
        }
    }

    private fun findResourceByDisplayName(displayName: String): AspireResource? =
        resourcesByDisplayName[displayName]
}
