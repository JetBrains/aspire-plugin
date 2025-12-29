package com.jetbrains.aspire.rider.orchestration

import com.jetbrains.rider.model.RdProjectType

internal class MauiProjectOrchestrationHandler : AspireProjectOrchestrationHandler {
    override val priority = 1
    override val supportedProjectTypes = listOf(RdProjectType.MAUI)
}