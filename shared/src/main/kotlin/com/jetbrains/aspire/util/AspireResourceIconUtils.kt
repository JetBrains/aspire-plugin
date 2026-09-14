package com.jetbrains.aspire.util

import com.intellij.icons.AllIcons
import com.jetbrains.aspire.extensions.AspireResourceIconProvider
import com.jetbrains.aspire.worker.ResourceType
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

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
