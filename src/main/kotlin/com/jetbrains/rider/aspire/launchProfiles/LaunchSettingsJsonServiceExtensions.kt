package com.jetbrains.rider.aspire.launchProfiles

import com.jetbrains.rider.model.RunnableProject
import com.jetbrains.rider.run.configurations.controls.LaunchProfile
import com.jetbrains.rider.run.configurations.launchSettings.LaunchSettingsJsonService

suspend fun LaunchSettingsJsonService.getProjectLaunchProfiles(runnableProject: RunnableProject): List<LaunchProfile> {
    val launchSettings = loadLaunchSettingsSuspend(runnableProject) ?: return emptyList()

    return launchSettings
        .profiles
        .orEmpty()
        .asSequence()
        .filter { it.value.commandName.equals("Project", true) }
        .map { (name, content) -> LaunchProfile(name, content) }
        .sortedBy { it.name }
        .toList()
}

suspend fun LaunchSettingsJsonService.getProjectLaunchProfileByName(
    runnableProject: RunnableProject,
    launchProfileName: String?
): LaunchProfile? {
    val launchSettings = loadLaunchSettingsSuspend(runnableProject) ?: return null

    return launchSettings
        .profiles
        .orEmpty()
        .asSequence()
        .filter { it.value.commandName.equals("Project", true) }
        .firstOrNull { it.key == launchProfileName }
        ?.let { (name, content) -> LaunchProfile(name, content) }
}