@file:Suppress("UnstableApiUsage")

package com.jetbrains.rider.aspire.util

import com.intellij.docker.DockerIcons
import com.intellij.icons.AllIcons
import com.intellij.ui.BadgeIconSupplier
import com.jetbrains.rider.aspire.generated.ResourceState
import com.jetbrains.rider.aspire.generated.ResourceType
import icons.RiderIcons
import javax.swing.Icon

internal fun getIcon(type: ResourceType, state: ResourceState?): Icon {
    var icon = when (type) {
        ResourceType.Project -> RiderIcons.RunConfigurations.DotNetProject
        ResourceType.Container -> DockerIcons.Docker
        ResourceType.Executable -> AllIcons.Nodes.Console
        ResourceType.Unknown -> AllIcons.RunConfigurations.TestUnknown
    }

    icon = when(state) {
        ResourceState.Running -> BadgeIconSupplier(icon).liveIndicatorIcon
        ResourceState.FailedToStart -> BadgeIconSupplier(icon).errorIcon
        else -> icon
    }

    return icon
}