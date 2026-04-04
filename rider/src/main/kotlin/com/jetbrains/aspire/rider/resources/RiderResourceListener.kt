package com.jetbrains.aspire.rider.resources

import com.intellij.openapi.project.Project
import com.jetbrains.aspire.dashboard.ResourceListener
import com.jetbrains.aspire.generated.ResourceCommandState
import com.jetbrains.aspire.rider.generated.AspireRdResource
import com.jetbrains.aspire.rider.generated.AspireRdResourceCommand
import com.jetbrains.aspire.rider.generated.AspireRdResourceCommandState
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
        state.commands.map {
            val state = when (it.state) {
                ResourceCommandState.Disabled -> AspireRdResourceCommandState.Disabled
                ResourceCommandState.Enabled -> AspireRdResourceCommandState.Enabled
                ResourceCommandState.Hidden -> AspireRdResourceCommandState.Hidden
            }
            AspireRdResourceCommand(it.name, it.displayName, state)
        }
    )
}
