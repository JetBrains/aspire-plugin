package me.rafaelldi.aspire.run

import com.jetbrains.rd.util.firstOrNull
import com.jetbrains.rider.model.RunnableProject
import com.jetbrains.rider.run.configurations.launchSettings.LaunchSettingsJson
import com.jetbrains.rider.run.configurations.launchSettings.LaunchSettingsJsonService

internal fun getLaunchProfileByNameOrFirst(
    runnableProject: RunnableProject,
    launchProfileName: String?
): Pair<String, LaunchSettingsJson.Profile>? {
    val launchProfiles = LaunchSettingsJsonService.loadLaunchSettings(runnableProject)
        ?.profiles
        ?.filter { it.value.commandName.equals("Project", true) }
        ?: return null
    val launchProfile =
        if (launchProfileName.isNullOrEmpty()) {
            launchProfiles.firstOrNull()?.toPair()
        } else {
            val profileByName = launchProfiles[launchProfileName]
            if (profileByName != null) launchProfileName to profileByName
            else launchProfiles.firstOrNull()?.toPair()
        }
    return launchProfile
}

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

internal fun getEnvironmentVariables(launchProfile: Pair<String, LaunchSettingsJson.Profile>): MutableMap<String, String> {
    val environmentVariables = launchProfile.second.environmentVariables
        ?.mapNotNull { it.value?.let { value -> it.key to value } }
        ?.toMap()
        ?.toMutableMap()
        ?: mutableMapOf()
    environmentVariables.put("DOTNET_LAUNCH_PROFILE", launchProfile.first)
    val applicationUrl = launchProfile.second.applicationUrl
    if (!applicationUrl.isNullOrEmpty()) {
        environmentVariables.put("ASPNETCORE_URLS", applicationUrl)
    }

    return environmentVariables
}

internal fun getApplicationUrl(launchProfile: Pair<String, LaunchSettingsJson.Profile>) : String? {
    val applicationUrl = launchProfile.second.applicationUrl
    return applicationUrl?.substringBefore(';')
}