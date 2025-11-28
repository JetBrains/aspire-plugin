package com.jetbrains.aspire.dashboard

import com.intellij.openapi.extensions.ExtensionPointName
import com.jetbrains.aspire.generated.ResourceType
import javax.swing.Icon

interface AspireResourceIconProviderExtension {
    companion object {
        val EP_NAME =
            ExtensionPointName<AspireResourceIconProviderExtension>("com.jetbrains.aspire.resourceIconProviderExtension")
    }

    fun getIcon(type: ResourceType, containerImage: String?): Icon?
}