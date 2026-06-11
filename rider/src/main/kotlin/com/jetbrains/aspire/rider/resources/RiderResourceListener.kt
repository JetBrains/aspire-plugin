package com.jetbrains.aspire.rider.resources

import com.intellij.openapi.project.Project
import com.jetbrains.aspire.dashboard.ResourceListener
import com.jetbrains.aspire.rider.generated.AspireRdResource
import com.jetbrains.aspire.rider.generated.AspireRdResourceCommand
import com.jetbrains.aspire.rider.generated.AspireRdResourceCommandState
import com.jetbrains.aspire.rider.generated.AspireRdResourceHealthStatus
import com.jetbrains.aspire.rider.generated.AspireRdResourceState
import com.jetbrains.aspire.rider.generated.AspireRdResourceStateStyle
import com.jetbrains.aspire.rider.generated.AspireRdResourceType
import com.jetbrains.aspire.rider.generated.aspirePluginModel
import com.jetbrains.aspire.worker.AspireResource
import com.jetbrains.aspire.worker.AspireResourceData
import com.jetbrains.aspire.worker.ResourceCommandState
import com.jetbrains.aspire.worker.ResourceHealthStatus
import com.jetbrains.aspire.worker.ResourceState
import com.jetbrains.aspire.worker.ResourceStateStyle
import com.jetbrains.aspire.worker.ResourceType
import com.jetbrains.rider.projectView.hasSolution
import com.jetbrains.rider.projectView.solution

internal class RiderResourceListener(private val project: Project) : ResourceListener {
    override fun resourceCreated(resource: AspireResource) {
        if (!project.hasSolution) return
        val state = resource.resourceState.value
        val rdResource = mapResource(state)
        project.solution.aspirePluginModel.resources[resource.resourceName] = rdResource
    }

    override fun resourceUpdated(resource: AspireResource) {
        if (!project.hasSolution) return
        val state = resource.resourceState.value
        val rdResource = mapResource(state)
        project.solution.aspirePluginModel.resources[resource.resourceName] = rdResource
    }

    override fun resourceDeleted(resource: AspireResource) {
        if (!project.hasSolution) return
        project.solution.aspirePluginModel.resources.remove(resource.resourceName)
    }

    private fun mapResource(state: AspireResourceData) = AspireRdResource(
        state.name,
        state.displayName,
        state.type.toRdType(),
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

    private fun ResourceType.toRdType() = when (this) {
        ResourceType.Project -> AspireRdResourceType.Project
        ResourceType.Container -> AspireRdResourceType.Container
        ResourceType.Executable -> AspireRdResourceType.Executable
        ResourceType.Parameter -> AspireRdResourceType.Parameter
        ResourceType.ExternalService -> AspireRdResourceType.ExternalService
        ResourceType.MongoDB -> AspireRdResourceType.MongoDB
        ResourceType.MySql -> AspireRdResourceType.MySql
        ResourceType.Postgres -> AspireRdResourceType.Postgres
        ResourceType.SqlServer -> AspireRdResourceType.SqlServer
        ResourceType.AzureStorageResource -> AspireRdResourceType.AzureStorageResource
        ResourceType.Unknown -> AspireRdResourceType.Unknown
    }
}
