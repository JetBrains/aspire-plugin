package com.jetbrains.aspire.rider.launchProfiles

import com.jetbrains.rider.model.RunnableProject
import com.jetbrains.rider.run.configurations.controls.LaunchProfile
import com.jetbrains.rider.run.configurations.launchSettings.LaunchSettingsJson
import com.jetbrains.rider.run.configurations.launchSettings.LaunchSettingsJsonService
import java.nio.file.Path
import kotlin.collections.orEmpty
import kotlin.io.path.nameWithoutExtension

fun LaunchSettingsJsonService.getFirstOrNullLaunchProfileProfile(runnableProject: RunnableProject): LaunchProfile? {
    val profiles = loadLaunchSettings(runnableProject)?.profiles ?: return null

    return profiles
        .asSequence().firstOrNull { it.value.commandName.equals("Project", true) }
        ?.let { LaunchProfile(it.key, it.value) }
}

suspend fun LaunchSettingsJsonService.getProjectLaunchProfiles(runnableProject: RunnableProject): List<LaunchProfile> =
    loadLaunchSettingsSuspend(runnableProject)?.let { getLaunchProfiles(it) }.orEmpty()


suspend fun LaunchSettingsJsonService.getLaunchProfiles(launchSettingsFilePath: Path): List<LaunchProfile> =
    loadLaunchSettingsSuspend(launchSettingsFilePath)?.let { getLaunchProfiles(it) }.orEmpty()

private fun getLaunchProfiles(launchSettings: LaunchSettingsJson.LaunchSettings): List<LaunchProfile> {
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

suspend fun LaunchSettingsJsonService.getProjectLaunchProfileByName(
    launchSettingsFilePath: Path,
    launchProfileName: String?
): LaunchProfile? =
    loadLaunchSettingsSuspend(launchSettingsFilePath)?.let { getProjectLaunchProfileByName(it, launchProfileName) }

private fun getProjectLaunchProfileByName(
    launchSettings: LaunchSettingsJson.LaunchSettings,
    launchProfileName: String?
): LaunchProfile? =
    launchSettings.profiles
        ?.asSequence()
        ?.filter { it.value.commandName.equals("Project", true) }
        ?.firstOrNull { it.key == launchProfileName }
        ?.let { (name, content) -> LaunchProfile(name, content) }

fun getLaunchSettingsPathForCsFile(csFilePath: Path): Path? =
    csFilePath.parent?.resolve("${csFilePath.nameWithoutExtension}.run.json")
