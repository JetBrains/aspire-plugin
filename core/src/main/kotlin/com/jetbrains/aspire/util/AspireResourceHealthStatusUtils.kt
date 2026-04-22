package com.jetbrains.aspire.util

import com.jetbrains.aspire.generated.dashboard.HealthReport
import com.jetbrains.aspire.generated.dashboard.HealthStatus
import com.jetbrains.aspire.worker.AspireResourceData
import com.jetbrains.aspire.worker.ResourceHealthStatus
import com.jetbrains.aspire.worker.ResourceState
import com.jetbrains.aspire.worker.ResourceStateStyle
import kotlin.collections.map

internal enum class ResourceIconBadge {
    None,
    Live,
    Warning,
    Error
}

internal fun calculateHealthStatus(
    state: ResourceState?,
    healthReports: List<HealthReport?>?
): ResourceHealthStatus? {
    if (state != ResourceState.Running) return null
    if (healthReports.isNullOrEmpty()) return ResourceHealthStatus.Healthy

    val statuses = healthReports.map { it?.status }
    if (statuses.any { it == null }) return ResourceHealthStatus.Unhealthy

    return when {
        statuses.any { it == HealthStatus.HEALTH_STATUS_UNHEALTHY } -> ResourceHealthStatus.Unhealthy
        statuses.any { it == HealthStatus.HEALTH_STATUS_DEGRADED } -> ResourceHealthStatus.Degraded
        else -> ResourceHealthStatus.Healthy
    }
}

internal fun getHealthStatusBadge(resourceData: AspireResourceData): ResourceIconBadge {
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

    if (resourceData.stateStyle != null && resourceData.stateStyle != ResourceStateStyle.Unknown) {
        return resourceData.stateStyle.toBadge()
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
