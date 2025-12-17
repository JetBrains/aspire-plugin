package com.jetbrains.aspire.dashboard

import com.intellij.openapi.extensions.ExtensionPointName
import com.jetbrains.aspire.generated.ResourceType
import javax.swing.Icon

interface AspireResourceIconProviderExtension {
    companion object {
        private val EP_NAME =
            ExtensionPointName<AspireResourceIconProviderExtension>("com.jetbrains.aspire.resourceIconProviderExtension")

        fun getAvailableProviders() : List<AspireResourceIconProviderExtension> {
            return EP_NAME.extensionList
        }
    }

    fun getIcon(type: ResourceType, containerImage: String?): Icon?
}