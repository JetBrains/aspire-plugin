package com.jetbrains.aspire.rider.orchestration

import com.intellij.openapi.extensions.ExtensionPointName
import com.jetbrains.rider.model.RdProjectType

interface AspireProjectOrchestrationHandler {
    companion object {
        private val EP_NAME =
            ExtensionPointName<AspireProjectOrchestrationHandler>("com.jetbrains.aspire.projectOrchestrationHandler")

        fun getHandlerForType(type: RdProjectType): AspireProjectOrchestrationHandler? {
            return EP_NAME.extensionList
                .sortedBy { it.priority }
                .firstOrNull { it.supportedProjectTypes.contains(type) }
        }
    }

    val priority: Int
    val supportedProjectTypes: List<RdProjectType>
}