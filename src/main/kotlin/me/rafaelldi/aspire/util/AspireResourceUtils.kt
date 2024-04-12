@file:Suppress("UnstableApiUsage")

package me.rafaelldi.aspire.util

import com.intellij.docker.DockerIcons
import com.intellij.icons.AllIcons
import com.intellij.ui.BadgeIconSupplier
import icons.RiderIcons
import me.rafaelldi.aspire.generated.ResourceState
import me.rafaelldi.aspire.generated.ResourceType
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