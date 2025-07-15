package com.jetbrains.rider.aspire.util

import com.jetbrains.rider.projectView.nodes.getUserData
import com.jetbrains.rider.projectView.workspace.ProjectModelEntity
import com.jetbrains.rider.projectView.workspace.isProject

private const val IS_ASPIRE_HOST = "IsAspireHost"
private const val IS_ASPIRE_SHARED_PROJECT = "IsAspireSharedProject"

fun ProjectModelEntity.isAspireHost(): Boolean {
    if (!isProject()) return false
    val isAspireHost = descriptor.getUserData(IS_ASPIRE_HOST)
    return isAspireHost?.equals("true", true) == true
}

fun ProjectModelEntity.isAspireSharedProject(): Boolean {
    if (!isProject()) return false
    val isAspireSharedProject = descriptor.getUserData(IS_ASPIRE_SHARED_PROJECT)
    return isAspireSharedProject?.equals("true", true) == true
}