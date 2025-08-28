package com.jetbrains.rider.aspire.orchestration

import com.jetbrains.rider.aspire.util.isAspireHostProject
import com.jetbrains.rider.aspire.util.isAspireSharedProject
import com.jetbrains.rider.model.RdProjectDescriptor
import com.jetbrains.rider.model.RdProjectType
import com.jetbrains.rider.projectView.workspace.ProjectModelEntity

fun ProjectModelEntity.isAspireOrchestrationSupported(): Boolean {
    if (isAspireHostProject() || isAspireSharedProject()) return false

    val entityDescriptor = descriptor
    if (entityDescriptor !is RdProjectDescriptor) return false

    if (entityDescriptor.specificType == RdProjectType.BlazorWasm) return false

    return entityDescriptor.isDotNetCore
}