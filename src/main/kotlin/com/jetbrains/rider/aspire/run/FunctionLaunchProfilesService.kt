package com.jetbrains.rider.aspire.run

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.rider.model.RunnableProject
import com.jetbrains.rider.run.configurations.controls.LaunchProfile
import com.jetbrains.rider.run.configurations.launchSettings.LaunchSettingsJsonService
import java.io.File
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class FunctionLaunchProfilesService(val project: Project) {
    companion object {
        fun getInstance(project: Project): FunctionLaunchProfilesService = project.service()
    }

    private val cache = ConcurrentHashMap<String, Pair<Long, List<LaunchProfile>>>()

    @RequiresBackgroundThread
    fun initialize(runnableProjects: List<RunnableProject>) {
        runnableProjects.forEach {
            val launchSettingsFile = LaunchSettingsJsonService.getLaunchSettingsFileForProject(it)
                ?: return@forEach
            val profiles = getLaunchProfiles(launchSettingsFile)
            cache[launchSettingsFile.absolutePath] = Pair(launchSettingsFile.lastModified(), profiles)
        }
    }

    @RequiresBackgroundThread
    fun getLaunchProfiles(runnableProject: RunnableProject): List<LaunchProfile> {
        val launchSettingsFile = LaunchSettingsJsonService.getLaunchSettingsFileForProject(runnableProject)
            ?: return emptyList()

        val launchSettingsFileStamp = launchSettingsFile.lastModified()
        val existingLaunchProfile = cache[launchSettingsFile.absolutePath]
        if (existingLaunchProfile == null || launchSettingsFileStamp != existingLaunchProfile.first || existingLaunchProfile.second.isEmpty()) {
            val profiles = getLaunchProfiles(launchSettingsFile)
            cache[launchSettingsFile.absolutePath] = Pair(launchSettingsFileStamp, profiles)
            return profiles
        }

        return existingLaunchProfile.second
    }

    @RequiresBackgroundThread
    fun getLaunchProfileByName(runnableProject: RunnableProject, launchProfileName: String?): LaunchProfile? =
        getLaunchProfiles(runnableProject).find { it.name == launchProfileName }

    @RequiresBackgroundThread
    private fun getLaunchProfiles(launchSettingsFile: File): List<LaunchProfile> {
        val launchSettings = LaunchSettingsJsonService.getInstance(project).loadLaunchSettings(launchSettingsFile)
            ?: return emptyList()

        return launchSettings
            .profiles
            .orEmpty()
            .asSequence()
            .filter { it.value.commandName.equals("Project", true) }
            .map { (name, content) -> LaunchProfile(name, content) }
            .sortedBy { it.name }
            .toList()
    }
}