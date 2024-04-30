package me.rafaelldi.aspire.run

import com.jetbrains.rider.model.RunnableProject
import com.jetbrains.rider.run.configurations.launchSettings.LaunchSettingsJson
import com.jetbrains.rider.run.configurations.launchSettings.LaunchSettingsJsonService

internal fun getLaunchProfileByName(
    runnableProject: RunnableProject,
    launchProfileName: String?
): Pair<String, LaunchSettingsJson.Profile>? {
    if (launchProfileName == null) return null
    val launchProfiles = LaunchSettingsJsonService.loadLaunchSettings(runnableProject)
        ?.profiles
        ?.filter { it.value.commandName.equals("Project", true) }
        ?: return null
    val profileByName = launchProfiles[launchProfileName] ?: return null
    return launchProfileName to profileByName
}

internal fun getEnvironmentVariables(launchProfileName: String, launchProfileContent: LaunchSettingsJson.Profile): MutableMap<String, String> {
    val environmentVariables = launchProfileContent.environmentVariables
        ?.mapNotNull { it.value?.let { value -> it.key to value } }
        ?.toMap()
        ?.toMutableMap()
        ?: mutableMapOf()
    environmentVariables["DOTNET_LAUNCH_PROFILE"] = launchProfileName
    val applicationUrl = launchProfileContent.applicationUrl
    if (!applicationUrl.isNullOrEmpty()) {
        environmentVariables["ASPNETCORE_URLS"] = applicationUrl
    }

    return environmentVariables
}

internal fun getApplicationUrl(launchProfileContent: LaunchSettingsJson.Profile) : String? {
    val applicationUrl = launchProfileContent.applicationUrl
    return applicationUrl?.substringBefore(';')
}