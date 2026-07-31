package com.jetbrains.aspire.rider.launchProfiles

import com.jetbrains.aspire.rider.run.file.ASPIRE_CONFIG_JSON_NAME
import com.jetbrains.rider.model.RunnableProject
import com.jetbrains.rider.run.configurations.controls.LaunchProfile
import com.jetbrains.rider.run.configurations.launchSettings.LaunchSettingsJson
import com.jetbrains.rider.run.configurations.launchSettings.LaunchSettingsJsonService
import java.nio.file.Path

fun LaunchSettingsJsonService.getFirstOrNullLaunchProfileProfile(runnableProject: RunnableProject): LaunchProfile? {
    val profiles = loadLaunchSettings(runnableProject)?.profiles ?: return null

    return profiles
        .asSequence()
        .firstOrNull { it.value.commandName.equals("Project", true) }
        ?.let { LaunchProfile(it.key, it.value) }
}

suspend fun LaunchSettingsJsonService.getProjectLaunchProfiles(runnableProject: RunnableProject): List<LaunchProfile> =
    loadLaunchSettingsSuspend(runnableProject)?.let { getProjectLaunchProfiles(it) }.orEmpty()

private fun getProjectLaunchProfiles(launchSettings: LaunchSettingsJson.LaunchSettings): List<LaunchProfile> {
    val profiles = launchSettings.profiles ?: return emptyList()

    return profiles
        .asSequence()
        .filter { it.value.commandName.equals("Project", true) }
        .map { (name, content) -> LaunchProfile(name, content) }
        .sortedBy { it.name }
        .toList()
}

suspend fun LaunchSettingsJsonService.getProjectLaunchProfileByName(
    runnableProject: RunnableProject,
    launchProfileName: String?
): LaunchProfile? =
    loadLaunchSettingsSuspend(runnableProject)?.let { getProjectLaunchProfileByName(it, launchProfileName) }

private fun getProjectLaunchProfileByName(
    launchSettings: LaunchSettingsJson.LaunchSettings,
    launchProfileName: String?
): LaunchProfile? =
    launchSettings.profiles
        ?.asSequence()
        ?.filter { it.value.commandName.equals("Project", true) }
        ?.firstOrNull { it.key == launchProfileName }
        ?.let { (name, content) -> LaunchProfile(name, content) }

/**
 * Loads launch profiles from a file-based app host configuration ([ASPIRE_CONFIG_JSON_NAME]).
 *
 * Unlike [getProjectLaunchProfiles], this does not filter by `commandName`, because `aspire.config.json`
 * profiles do not declare one.
 */
suspend fun LaunchSettingsJsonService.getFileBasedLaunchProfiles(launchSettingsFilePath: Path): List<LaunchProfile> =
    loadLaunchSettingsSuspend(launchSettingsFilePath)?.let { getFileBasedLaunchProfiles(it) }.orEmpty()

private fun getFileBasedLaunchProfiles(launchSettings: LaunchSettingsJson.LaunchSettings): List<LaunchProfile> {
    val profiles = launchSettings.profiles ?: return emptyList()

    return profiles
        .map { (name, content) -> LaunchProfile(name, content) }
        .sortedBy { it.name }
        .toList()
}

suspend fun LaunchSettingsJsonService.getFileBasedLaunchProfileByName(
    launchSettingsFilePath: Path,
    launchProfileName: String?
): LaunchProfile? =
    loadLaunchSettingsSuspend(launchSettingsFilePath)?.let { getFileBasedLaunchProfileByName(it, launchProfileName) }

private fun getFileBasedLaunchProfileByName(
    launchSettings: LaunchSettingsJson.LaunchSettings,
    launchProfileName: String?
): LaunchProfile? =
    launchSettings.profiles
        ?.entries
        ?.firstOrNull { it.key == launchProfileName }
        ?.let { (name, content) -> LaunchProfile(name, content) }

fun getLaunchSettingsPathForCsFile(csFilePath: Path): Path? =
    csFilePath.parent?.resolve(ASPIRE_CONFIG_JSON_NAME)
