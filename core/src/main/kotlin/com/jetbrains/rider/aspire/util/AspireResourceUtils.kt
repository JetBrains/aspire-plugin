@file:Suppress("UnstableApiUsage")

package com.jetbrains.rider.aspire.util

import com.intellij.icons.AllIcons
import com.intellij.ui.BadgeIconSupplier
import com.jetbrains.rider.aspire.generated.ResourceHealthStatus
import com.jetbrains.rider.aspire.generated.ResourceState
import com.jetbrains.rider.aspire.generated.ResourceType
import com.jetbrains.rider.aspire.dashboard.AspireResource
import com.jetbrains.rider.aspire.dashboard.AspireResourceIconProviderExtension
import icons.ReSharperIcons
import icons.RiderIcons
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

internal fun getIcon(aspireResource: AspireResource): Icon {
    val baseIcon = getIcon(aspireResource.type, aspireResource.containerImage?.value)

    val icon = when (aspireResource.state) {
        ResourceState.FailedToStart -> BadgeIconSupplier(baseIcon).errorIcon
        ResourceState.RuntimeUnhealthy -> BadgeIconSupplier(baseIcon).errorIcon
        ResourceState.Waiting -> BadgeIconSupplier(baseIcon).warningIcon
        ResourceState.Running -> {
            if (aspireResource.healthStatus == ResourceHealthStatus.Healthy || aspireResource.healthStatus == null) {
                BadgeIconSupplier(baseIcon).liveIndicatorIcon
            } else {
                BadgeIconSupplier(baseIcon).warningIcon
            }
        }

        else -> baseIcon
    }

    return icon
}

@ApiStatus.Internal
fun getIcon(type: ResourceType, containerImage: String?): Icon {
    AspireResourceIconProviderExtension.EP_NAME.extensionList.forEach {
        val icon = it.getIcon(type, containerImage)
        if (icon != null) return icon
    }

    return AllIcons.FileTypes.Unknown
}

internal class BaseAspireResourceIconProvider : AspireResourceIconProviderExtension {
    override fun getIcon(type: ResourceType, containerImage: String?) = when (type) {
        ResourceType.Project -> RiderIcons.RunConfigurations.DotNetProject
        ResourceType.Executable -> AllIcons.Nodes.Console
        ResourceType.Parameter -> ReSharperIcons.PsiSymbols.Parameter
        ResourceType.ExternalService -> AllIcons.General.Web
        ResourceType.Unknown -> AllIcons.FileTypes.Unknown
        else -> null
    }
}
