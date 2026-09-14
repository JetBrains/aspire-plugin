package com.jetbrains.aspire.util

import com.jetbrains.aspire.worker.AspireAppHost
import com.jetbrains.aspire.worker.AspireResource
import com.jetbrains.aspire.worker.AspireWorker
import com.jetbrains.aspire.worker.ResourceType
import com.jetbrains.aspire.worker.toNioPath
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
fun AspireAppHost.getAllResources(): List<AspireResource> =
    rootResources.value.flatMap { it.withDescendants() }

private fun AspireResource.withDescendants(): List<AspireResource> =
    listOf(this) + childrenResources.value.flatMap { it.withDescendants() }

@ApiStatus.Internal
fun AspireAppHost.findResource(predicate: (AspireResource) -> Boolean): AspireResource? =
    rootResources.value.findInTree(predicate)

@ApiStatus.Internal
fun AspireWorker.findProjectResource(projectPath: Path): AspireResource? =
    appHosts.value.firstNotNullOfOrNull { host ->
        host.findResource {
            val data = it.resourceState.value
            data.type == ResourceType.Project && data.projectPath?.value?.toNioPath() == projectPath
        }
    }

private fun List<AspireResource>.findInTree(predicate: (AspireResource) -> Boolean): AspireResource? {
    for (resource in this) {
        if (predicate(resource)) return resource
    }

    for (resource in this) {
        val found = resource.childrenResources.value.findInTree(predicate)
        if (found != null) return found
    }

    return null
}
