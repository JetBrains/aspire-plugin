package com.jetbrains.rider.aspire.dashboard

import com.intellij.openapi.extensions.ExtensionPointName
import com.jetbrains.rider.aspire.generated.ResourceType
import javax.swing.Icon

interface AspireResourceIconProviderExtension {
    companion object {
        val EP_NAME =
            ExtensionPointName<AspireResourceIconProviderExtension>("com.jetbrains.rider.aspire.resourceIconProviderExtension")
    }

    fun getIcon(type: ResourceType, containerImage: String?): Icon?
}