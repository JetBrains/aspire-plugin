package com.jetbrains.aspire.extensions

import com.intellij.openapi.extensions.ExtensionPointName
import com.jetbrains.aspire.worker.ResourceLaunchConfigurationType
import com.jetbrains.aspire.worker.ResourceKind
import javax.swing.Icon

interface AspireResourceIconProvider {
    companion object {
        private val EP_NAME =
            ExtensionPointName<AspireResourceIconProvider>("com.jetbrains.aspire.resourceIconProvider")

        fun getAvailableProviders(): List<AspireResourceIconProvider> {
            return EP_NAME.extensionList.sortedByDescending { it.priority }
        }
    }

    val priority: Int

    fun getIcon(type: ResourceKind, configurationType: ResourceLaunchConfigurationType, containerImage: String?): Icon?
}
