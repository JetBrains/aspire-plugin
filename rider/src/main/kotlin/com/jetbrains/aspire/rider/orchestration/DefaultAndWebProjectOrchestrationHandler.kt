package com.jetbrains.aspire.rider.orchestration

import com.jetbrains.rider.model.RdProjectType

internal class DefaultAndWebProjectOrchestrationHandler: AspireProjectOrchestrationHandler {
    override val priority = 0
    override val supportedProjectTypes = listOf(RdProjectType.Default, RdProjectType.Web)
}