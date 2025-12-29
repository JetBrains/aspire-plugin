package com.jetbrains.aspire.dashboard

import com.intellij.openapi.extensions.ExtensionPointName
import com.jetbrains.aspire.generated.ResourceType
import javax.swing.Icon

interface AspireResourceIconProvider {
    companion object {
        private val EP_NAME =
            ExtensionPointName<AspireResourceIconProvider>("com.jetbrains.aspire.resourceIconProvider")

        fun getAvailableProviders() : List<AspireResourceIconProvider> {
            return EP_NAME.extensionList
        }
    }

    fun getIcon(type: ResourceType, containerImage: String?): Icon?
}