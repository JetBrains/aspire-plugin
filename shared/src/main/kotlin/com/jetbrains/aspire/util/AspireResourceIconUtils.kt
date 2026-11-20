package com.jetbrains.aspire.util

import com.intellij.icons.AllIcons
import com.jetbrains.aspire.extensions.AspireResourceIconProvider
import com.jetbrains.aspire.worker.ResourceLaunchConfigurationType
import com.jetbrains.aspire.worker.ResourceKind
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Internal
fun getResourceIcon(
    type: ResourceKind,
    configurationType: ResourceLaunchConfigurationType,
    containerImage: String?
): Icon {
    AspireResourceIconProvider.getAvailableProviders().forEach {
        val icon = it.getIcon(type, configurationType, containerImage)
        if (icon != null) return icon
    }

    return AllIcons.FileTypes.Unknown
}

internal class BaseAspireResourceIconProvider : AspireResourceIconProvider {
    override val priority = 0

    override fun getIcon(
        type: ResourceKind,
        configurationType: ResourceLaunchConfigurationType,
        containerImage: String?
    ) = when (type) {
        ResourceKind.Executable -> AllIcons.Nodes.Console
        ResourceKind.Parameter -> AllIcons.Nodes.Parameter
        ResourceKind.ExternalService -> AllIcons.General.Web
        ResourceKind.Container -> AllIcons.FileTypes.Docker
        ResourceKind.Unknown -> AllIcons.FileTypes.Unknown
        else -> null
    }
}
