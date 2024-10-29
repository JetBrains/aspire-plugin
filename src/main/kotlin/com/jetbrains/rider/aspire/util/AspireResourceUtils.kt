@file:Suppress("UnstableApiUsage")

package com.jetbrains.rider.aspire.util

import com.intellij.docker.DockerIcons
import com.intellij.icons.AllIcons
import com.intellij.ui.BadgeIconSupplier
import com.jetbrains.rider.aspire.generated.ResourceHealthStatus
import com.jetbrains.rider.aspire.generated.ResourceState
import com.jetbrains.rider.aspire.generated.ResourceType
import icons.RiderIcons
import javax.swing.Icon

internal fun getIcon(type: ResourceType, state: ResourceState?, healthStatus: ResourceHealthStatus?): Icon {
    var icon = when (type) {
        ResourceType.Project -> RiderIcons.RunConfigurations.DotNetProject
        ResourceType.Container -> DockerIcons.Docker
        ResourceType.Executable -> AllIcons.Nodes.Console
        ResourceType.Unknown -> AllIcons.RunConfigurations.TestUnknown
    }

    icon = when(state) {
        ResourceState.Exited -> icon
        ResourceState.Finished -> icon
        ResourceState.FailedToStart -> BadgeIconSupplier(icon).errorIcon
        ResourceState.Running -> {
            if (healthStatus == ResourceHealthStatus.Healthy) {
                BadgeIconSupplier(icon).liveIndicatorIcon
            } else {
                BadgeIconSupplier(icon).warningIcon
            }
        }
        else -> icon
    }

    return icon
}