package com.jetbrains.aspire.rider.orchestration

import com.jetbrains.rider.model.RdProjectType

internal class AzureFunctionOrchestrationHandler : BaseOrchestrationHandler() {
    override val priority = 1
    override val requiresProjectReference = false
    override val supportedProjectTypes = listOf(RdProjectType.AzureFunction)

    override val appHostPackageName = AZURE_FUNCTIONS_HOSTING_PACKAGE_NAME
    override val appHostPackageVersion = AZURE_FUNCTIONS_HOSTING_PACKAGE_VERSION

    override fun buildAppHostResourceCode(resourceName: String, relativeProjectPath: String) = buildString {
        appendLine("builder.AddAzureFunctionsProject(\"$resourceName\", \"$relativeProjectPath\")")
        append("    .WithExternalHttpEndpoints();")
    }
}
