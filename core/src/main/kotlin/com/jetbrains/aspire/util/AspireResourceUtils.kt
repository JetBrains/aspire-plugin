@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.util

import com.intellij.icons.AllIcons
import com.intellij.ui.BadgeIconSupplier
import com.jetbrains.aspire.extensions.AspireResourceIconProvider
import com.jetbrains.aspire.worker.AspireAppHost
import com.jetbrains.aspire.worker.AspireResource
import com.jetbrains.aspire.worker.AspireResourceData
import com.jetbrains.aspire.worker.AspireWorker
import com.jetbrains.aspire.worker.ResourceType
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import javax.swing.Icon

internal fun getIcon(resourceData: AspireResourceData): Icon {
    val baseIcon = getResourceIcon(resourceData.type, resourceData.containerImage?.value)

    return when (getHealthStatusBadge(resourceData)) {
        ResourceIconBadge.Error -> BadgeIconSupplier(baseIcon).errorIcon
        ResourceIconBadge.Warning -> BadgeIconSupplier(baseIcon).warningIcon
        ResourceIconBadge.Live -> BadgeIconSupplier(baseIcon).liveIndicatorIcon
        ResourceIconBadge.None -> baseIcon
    }
}

@ApiStatus.Internal
fun getResourceIcon(type: ResourceType, containerImage: String?): Icon {
    AspireResourceIconProvider.getAvailableProviders().forEach {
        val icon = it.getIcon(type, containerImage)
        if (icon != null) return icon
    }

    return AllIcons.FileTypes.Unknown
}

internal class BaseAspireResourceIconProvider : AspireResourceIconProvider {
    override val priority = 0

    override fun getIcon(type: ResourceType, containerImage: String?) = when (type) {
        ResourceType.Executable -> AllIcons.Nodes.Console
        ResourceType.Parameter -> AllIcons.Nodes.Parameter
        ResourceType.ExternalService -> AllIcons.General.Web
        ResourceType.Container -> AllIcons.FileTypes.Docker
        ResourceType.Unknown -> AllIcons.FileTypes.Unknown
        else -> null
    }
}

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
            data.type == ResourceType.Project && data.projectPath?.value == projectPath
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
