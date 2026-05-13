package com.jetbrains.aspire.rider.resources

import com.jetbrains.aspire.dashboard.AspireResourceIconProvider
import com.jetbrains.aspire.worker.ResourceType
import icons.ReSharperIcons
import icons.RiderIcons

internal class DotNetAspireResourceIconProvider : AspireResourceIconProvider {
    override val priority = 5

    override fun getIcon(type: ResourceType, containerImage: String?) = when (type) {
        ResourceType.Project -> RiderIcons.RunConfigurations.DotNetProject
        ResourceType.AzureStorageResource -> ReSharperIcons.AzureStorage.MicrosoftStorageAzuriteDefault
        else -> null
    }
}