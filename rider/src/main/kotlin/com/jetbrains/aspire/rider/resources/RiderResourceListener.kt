package com.jetbrains.aspire.rider.resources

import com.intellij.openapi.project.Project
import com.jetbrains.aspire.dashboard.ResourceListener
import com.jetbrains.aspire.generated.ResourceCommandState
import com.jetbrains.aspire.generated.ResourceHealthStatus
import com.jetbrains.aspire.generated.ResourceState
import com.jetbrains.aspire.generated.ResourceStateStyle
import com.jetbrains.aspire.rider.generated.AspireRdResource
import com.jetbrains.aspire.rider.generated.AspireRdResourceCommand
import com.jetbrains.aspire.rider.generated.AspireRdResourceCommandState
import com.jetbrains.aspire.rider.generated.AspireRdResourceHealthStatus
import com.jetbrains.aspire.rider.generated.AspireRdResourceState
import com.jetbrains.aspire.rider.generated.AspireRdResourceStateStyle
import com.jetbrains.aspire.rider.generated.aspirePluginModel
import com.jetbrains.aspire.worker.AspireResource
import com.jetbrains.aspire.worker.AspireResourceData
import com.jetbrains.rider.projectView.solution

internal class RiderResourceListener(private val project: Project) : ResourceListener {
    override fun resourceCreated(resource: AspireResource) {
        val state = resource.resourceState.value
        val rdResource = mapResource(state)
        project.solution.aspirePluginModel.resources[resource.resourceId] = rdResource
    }

    override fun resourceUpdated(resource: AspireResource) {
        val state = resource.resourceState.value
        val rdResource = mapResource(state)
        project.solution.aspirePluginModel.resources[resource.resourceId] = rdResource
    }

    override fun resourceDeleted(resource: AspireResource) {
        project.solution.aspirePluginModel.resources.remove(resource.resourceId)
    }

    private fun mapResource(state: AspireResourceData) = AspireRdResource(
        state.name,
        state.displayName,
        state.state?.toRdState(),
        state.stateStyle?.toRdStateStyle(),
        state.healthStatus?.toRdHealthStatus(),
        state.exitCode?.value,
        state.commands.map {
            val state = when (it.state) {
                ResourceCommandState.Disabled -> AspireRdResourceCommandState.Disabled
                ResourceCommandState.Enabled -> AspireRdResourceCommandState.Enabled
                ResourceCommandState.Hidden -> AspireRdResourceCommandState.Hidden
            }
            AspireRdResourceCommand(it.name, it.displayName, state)
        }
    )

    private fun ResourceState.toRdState() = when (this) {
        ResourceState.Building -> AspireRdResourceState.Building
        ResourceState.Starting -> AspireRdResourceState.Starting
        ResourceState.Running -> AspireRdResourceState.Running
        ResourceState.FailedToStart -> AspireRdResourceState.FailedToStart
        ResourceState.RuntimeUnhealthy -> AspireRdResourceState.RuntimeUnhealthy
        ResourceState.Stopping -> AspireRdResourceState.Stopping
        ResourceState.Exited -> AspireRdResourceState.Exited
        ResourceState.Finished -> AspireRdResourceState.Finished
        ResourceState.Waiting -> AspireRdResourceState.Waiting
        ResourceState.NotStarted -> AspireRdResourceState.NotStarted
        ResourceState.Hidden -> AspireRdResourceState.Hidden
        ResourceState.Unknown -> AspireRdResourceState.Unknown
    }

    private fun ResourceStateStyle.toRdStateStyle() = when (this) {
        ResourceStateStyle.Success -> AspireRdResourceStateStyle.Success
        ResourceStateStyle.Info -> AspireRdResourceStateStyle.Info
        ResourceStateStyle.Warning -> AspireRdResourceStateStyle.Warning
        ResourceStateStyle.Error -> AspireRdResourceStateStyle.Error
        ResourceStateStyle.Unknown -> AspireRdResourceStateStyle.Unknown
    }

    private fun ResourceHealthStatus.toRdHealthStatus() = when (this) {
        ResourceHealthStatus.Healthy -> AspireRdResourceHealthStatus.Healthy
        ResourceHealthStatus.Unhealthy -> AspireRdResourceHealthStatus.Unhealthy
        ResourceHealthStatus.Degraded -> AspireRdResourceHealthStatus.Degraded
    }
}
