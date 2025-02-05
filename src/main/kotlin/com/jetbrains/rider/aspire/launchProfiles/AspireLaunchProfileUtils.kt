package com.jetbrains.rider.aspire.launchProfiles

import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.io.systemIndependentPath
import com.jetbrains.rider.aspire.util.MSBuildPropertyService.ProjectRunProperties
import com.jetbrains.rider.model.ProjectOutput
import com.jetbrains.rider.run.configurations.launchSettings.LaunchSettingsJson

internal fun getArguments(profile: LaunchSettingsJson.Profile?, projectOutput: ProjectOutput?) = buildString {
    val defaultArguments = projectOutput?.defaultArguments
    val profileArgs = profile?.commandLineArgs

    if (!defaultArguments.isNullOrEmpty()) {
        append(ParametersListUtil.join(defaultArguments))
        append(" ")
    }

    if (!profileArgs.isNullOrEmpty()) {
        append(profileArgs)
    }
}

internal fun getWorkingDirectory(profile: LaunchSettingsJson.Profile?, projectOutput: ProjectOutput?): String {
    return profile?.workingDirectory ?: projectOutput?.workingDirectory ?: ""
}

internal fun getWorkingDirectory(
    profile: LaunchSettingsJson.Profile?,
    projectProperties: ProjectRunProperties?
): String {
    return profile?.workingDirectory ?: projectProperties?.workingDirectory?.systemIndependentPath ?: ""
}

internal fun getEnvironmentVariables(
    profileName: String?,
    profile: LaunchSettingsJson.Profile?
) = buildMap {
    profile?.environmentVariables?.forEach {
        if (it.value != null) {
            put(it.key, it.value)
        }
    }

    if (!profileName.isNullOrEmpty()) {
        put("DOTNET_LAUNCH_PROFILE", profileName)
    }

    val applicationUrl = profile?.applicationUrl
    if (!applicationUrl.isNullOrEmpty()) {
        put("ASPNETCORE_URLS", applicationUrl)
    }
}

internal fun getApplicationUrl(profile: LaunchSettingsJson.Profile?): String {
    val applicationUrl = profile?.applicationUrl
    return applicationUrl?.substringBefore(';') ?: ""
}

internal fun getLaunchBrowserFlag(profile: LaunchSettingsJson.Profile?): Boolean {
    val launchBrowser = profile?.launchBrowser
    return launchBrowser == true
}