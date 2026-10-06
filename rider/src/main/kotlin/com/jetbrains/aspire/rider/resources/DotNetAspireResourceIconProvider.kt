package com.jetbrains.aspire.rider.resources

import com.jetbrains.aspire.extensions.AspireResourceIconProvider
import com.jetbrains.aspire.worker.ResourceLaunchConfigurationType
import com.jetbrains.aspire.worker.ResourceType
import icons.ReSharperIcons
import icons.RiderIcons

internal class DotNetAspireResourceIconProvider : AspireResourceIconProvider {
    override val priority = 5

    override fun getIcon(
        type: ResourceType,
        configurationType: ResourceLaunchConfigurationType,
        containerImage: String?
    ) = when (type) {
        ResourceType.Project -> when (configurationType) {
            ResourceLaunchConfigurationType.Project -> RiderIcons.RunConfigurations.DotNetProject
            ResourceLaunchConfigurationType.AzureFunctions -> ReSharperIcons.AzureFrontend.FunctionAppTemplateAzureFunc
            ResourceLaunchConfigurationType.Unknown -> RiderIcons.RunConfigurations.DotNetProject
        }

        ResourceType.AzureStorageResource -> ReSharperIcons.AzureStorage.MicrosoftStorageAzuriteDefault
        else -> null
    }
}
