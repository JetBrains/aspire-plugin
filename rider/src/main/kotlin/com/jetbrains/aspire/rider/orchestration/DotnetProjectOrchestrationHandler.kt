package com.jetbrains.aspire.rider.orchestration

import com.jetbrains.rider.model.RdProjectType

internal class DotnetProjectOrchestrationHandler : BaseOrchestrationHandler() {
    override val priority = 0
    override val requiresProjectReference = false
    override val supportedProjectTypes = listOf(
        RdProjectType.Default,
        RdProjectType.Web,
        RdProjectType.XamlProject,
        RdProjectType.Wpf
    )

    override val appHostPackageName = DOTNET_HOSTING_PACKAGE_NAME
    override val appHostPackageVersion = DOTNET_HOSTING_PACKAGE_VERSION

    override fun buildAppHostResourceCode(resourceName: String, relativeProjectPath: String) =
        "builder.AddDotnetProject(\"$resourceName\", \"$relativeProjectPath\");"
}
