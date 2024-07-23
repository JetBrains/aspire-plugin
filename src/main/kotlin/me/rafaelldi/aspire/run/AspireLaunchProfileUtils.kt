package me.rafaelldi.aspire.run

import com.intellij.util.execution.ParametersListUtil
import com.jetbrains.rider.model.ProjectOutput
import com.jetbrains.rider.run.configurations.launchSettings.LaunchSettingsJson


internal fun getArguments(profile: LaunchSettingsJson.Profile?, projectOutput: ProjectOutput?): String {
    val defaultArguments = projectOutput?.defaultArguments
    return if (defaultArguments.isNullOrEmpty()) profile?.commandLineArgs ?: ""
    else {
        val parametersList = ParametersListUtil.join(defaultArguments)
        val commandLineArgs = profile?.commandLineArgs
        if (commandLineArgs != null) {
            "$parametersList $commandLineArgs"
        } else {
            parametersList
        }
    }
}

internal fun getWorkingDirectory(profile: LaunchSettingsJson.Profile?, projectOutput: ProjectOutput?): String {
    return profile?.workingDirectory ?: projectOutput?.workingDirectory ?: ""
}

internal fun getEnvironmentVariables(profileName: String?, profile: LaunchSettingsJson.Profile?): MutableMap<String, String> {
    val environmentVariables = profile
        ?.environmentVariables
        ?.mapNotNull { it.value?.let { value -> it.key to value } }
        ?.toMap()
        ?.toMutableMap()
        ?: mutableMapOf()
    if (profileName != null) {
        environmentVariables["DOTNET_LAUNCH_PROFILE"] = profileName
    }
    val applicationUrl = profile?.applicationUrl
    if (!applicationUrl.isNullOrEmpty()) {
        environmentVariables["ASPNETCORE_URLS"] = applicationUrl
    }

    return environmentVariables
}

internal fun getApplicationUrl(profile: LaunchSettingsJson.Profile) : String {
    val applicationUrl = profile.applicationUrl
    return applicationUrl?.substringBefore(';') ?: ""
}