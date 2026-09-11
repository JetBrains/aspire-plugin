@file:Suppress("UnstableApiUsage")

package com.jetbrains.aspire.services

import com.intellij.ui.BadgeIconSupplier
import com.jetbrains.aspire.util.getResourceIcon
import com.jetbrains.aspire.worker.AspireResourceData
import com.jetbrains.aspire.worker.ResourceHealthStatus
import com.jetbrains.aspire.worker.ResourceState
import com.jetbrains.aspire.worker.ResourceStateStyle
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

private fun getHealthStatusBadge(resourceData: AspireResourceData): ResourceIconBadge {
    val state = resourceData.state ?: return ResourceIconBadge.None

    if (state.isStoppedState()) {
        return when {
            resourceData.exitCode?.value?.let { it != 0 } == true -> ResourceIconBadge.Error
            state == ResourceState.FailedToStart -> ResourceIconBadge.Warning
            else -> ResourceIconBadge.None
        }
    }

    if (state.isInformationalState()) {
        return ResourceIconBadge.None
    }

    if (state == ResourceState.RuntimeUnhealthy) {
        return ResourceIconBadge.Warning
    }

    val stateStyle = resourceData.stateStyle
    if (stateStyle != null && stateStyle != ResourceStateStyle.Unknown) {
        return stateStyle.toBadge()
    }

    if (resourceData.healthStatus == ResourceHealthStatus.Unhealthy || resourceData.healthStatus == ResourceHealthStatus.Degraded) {
        return ResourceIconBadge.Warning
    }

    return if (state == ResourceState.Running) ResourceIconBadge.Live else ResourceIconBadge.None
}

private fun ResourceState.isStoppedState(): Boolean =
    this == ResourceState.Exited || this == ResourceState.Finished || this == ResourceState.FailedToStart

private fun ResourceState.isInformationalState(): Boolean =
    this == ResourceState.Starting ||
            this == ResourceState.Building ||
            this == ResourceState.Waiting ||
            this == ResourceState.Stopping ||
            this == ResourceState.NotStarted ||
            this == ResourceState.Unknown

private fun ResourceStateStyle.toBadge(): ResourceIconBadge = when (this) {
    ResourceStateStyle.Error -> ResourceIconBadge.Error
    ResourceStateStyle.Warning -> ResourceIconBadge.Warning
    ResourceStateStyle.Success -> ResourceIconBadge.Live
    ResourceStateStyle.Info, ResourceStateStyle.Unknown -> ResourceIconBadge.None
}

private enum class ResourceIconBadge {
    None,
    Live,
    Warning,
    Error
}
