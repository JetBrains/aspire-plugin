package com.jetbrains.rider.aspire.orchestration

import com.intellij.platform.backend.workspace.virtualFile
import com.jetbrains.rider.aspire.util.isAspireHostProject
import com.jetbrains.rider.aspire.util.isAspireSharedProject
import com.jetbrains.rider.model.RdProjectDescriptor
import com.jetbrains.rider.model.RdProjectType
import com.jetbrains.rider.projectView.workspace.ProjectModelEntity

fun ProjectModelEntity.isAspireOrchestrationSupported(): Boolean {
    if (isAspireHostProject() || isAspireSharedProject()) return false

    val entityDescriptor = descriptor
    if (entityDescriptor !is RdProjectDescriptor) return false

    val extension = url?.virtualFile?.extension ?: return false
    if (extension != "csproj" && extension != "fsproj") return false

    val type = entityDescriptor.specificType
    if (type != RdProjectType.Default && type != RdProjectType.Web && type != RdProjectType.XamlProject) return false

    return entityDescriptor.isDotNetCore
}