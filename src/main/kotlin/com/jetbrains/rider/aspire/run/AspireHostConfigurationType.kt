package com.jetbrains.rider.aspire.run

import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.aspire.AspireIcons
import com.jetbrains.rider.aspire.util.getProjectLaunchProfileByName
import com.jetbrains.rider.model.ProjectOutput
import com.jetbrains.rider.model.RunnableProject
import com.jetbrains.rider.model.RunnableProjectKind
import com.jetbrains.rider.run.AutoGeneratedRunConfigurationManager
import com.jetbrains.rider.run.configurations.IRunConfigurationWithDefault
import com.jetbrains.rider.run.configurations.IRunnableProjectConfigurationType
import com.jetbrains.rider.run.configurations.RunConfigurationHelper.hasConfigurationForNameAndTypeId
import com.jetbrains.rider.run.configurations.controls.LaunchProfile
import com.jetbrains.rider.run.configurations.launchSettings.LaunchSettingsJson
import com.jetbrains.rider.run.configurations.launchSettings.LaunchSettingsJsonService

class AspireHostConfigurationType : ConfigurationTypeBase(
    ID,
    "Aspire Host",
    "Aspire Host configuration",
    AspireIcons.RunConfig
), IRunnableProjectConfigurationType, IRunConfigurationWithDefault {
    companion object {
        const val ID = "AspireHostConfiguration"
    }

    val factory = AspireHostConfigurationFactory(this)

    init {
        addFactory(factory)
    }

    override fun isApplicable(kind: RunnableProjectKind) = kind == AspireRunnableProjectKinds.AspireHost

    override suspend fun tryCreateDefault(
        project: Project,
        lifetime: Lifetime,
        projects: List<RunnableProject>,
        autoGeneratedRunConfigurationManager: AutoGeneratedRunConfigurationManager,
        runManager: RunManager
    ): List<Pair<RunnableProject, RunnerAndConfigurationSettings>> {
        val aspireHostProjects = projects.filter { it.kind == AspireRunnableProjectKinds.AspireHost }
        if (aspireHostProjects.isEmpty()) return emptyList()

        val service = LaunchSettingsJsonService.getInstance(project)
        val result = mutableListOf<Pair<RunnableProject, RunnerAndConfigurationSettings>>()

        for (runnableProject in aspireHostProjects) {
            val profiles = service.loadLaunchSettingsSuspend(runnableProject)?.profiles ?: continue

            generateConfigurationForProfiles(
                profiles,
                runnableProject,
                runManager,
                autoGeneratedRunConfigurationManager,
                project
            ).forEach {
                result.add(runnableProject to it)
            }
        }

        return result
    }

    private suspend fun generateConfigurationForProfiles(
        launchProfiles: Map<String, LaunchSettingsJson.Profile>,
        runnableProject: RunnableProject,
        runManager: RunManager,
        autoGeneratedRunConfigurationManager: AutoGeneratedRunConfigurationManager,
        project: Project
    ): List<RunnerAndConfigurationSettings> {
        val configurations = mutableListOf<RunnerAndConfigurationSettings>()

        for (profile in launchProfiles) {
            if (!profile.value.commandName.equals("Project", true))
                continue

            if (hasRunConfigurationEverBeenGenerated(
                    autoGeneratedRunConfigurationManager,
                    runnableProject.projectFilePath,
                    profile.key
                )
            ) continue

            val configurationName =
                if (runnableProject.name == profile.key) profile.key
                else "${runnableProject.name}: ${profile.key}"

            if (runManager.hasConfigurationForNameAndTypeId(configurationName, ID) ||
                runManager.hasConfigurationForNameAndTypeId(runnableProject.name, ID)
            ) continue

            val configuration = generateConfigurationForProfile(
                configurationName,
                runnableProject,
                profile.key,
                runManager,
                project
            )

            runManager.addConfiguration(configuration)
            markProjectAsAutoGenerated(
                autoGeneratedRunConfigurationManager,
                runnableProject.projectFilePath,
                profile.key
            )

            configurations.add(configuration)
        }

        return configurations
    }

    private fun hasRunConfigurationEverBeenGenerated(
        autoGeneratedRunConfigurationManager: AutoGeneratedRunConfigurationManager,
        projectFilePath: String,
        profileName: String
    ) = autoGeneratedRunConfigurationManager.hasRunConfigurationEverBeenGenerated(
        projectFilePath,
        mapOf(
            "aspireProfileName" to profileName,
        )
    )

    private fun markProjectAsAutoGenerated(
        autoGeneratedRunConfigurationManager: AutoGeneratedRunConfigurationManager,
        projectFilePath: String,
        profileName: String
    ) {
        autoGeneratedRunConfigurationManager.markProjectAsAutoGenerated(
            projectFilePath,
            mapOf(
                "aspireProfileName" to profileName,
            )
        )
    }

    private suspend fun generateConfigurationForProfile(
        name: String,
        runnableProject: RunnableProject,
        profile: String,
        runManager: RunManager,
        project: Project
    ): RunnerAndConfigurationSettings {
        val settings = runManager.createConfiguration(name, factory).apply {
            isActivateToolWindowBeforeRun = false
            isFocusToolWindowBeforeRun = false
        }
        val projectOutput = runnableProject.projectOutputs.firstOrNull()
        val launchProfile = LaunchSettingsJsonService
            .getInstance(project)
            .getProjectLaunchProfileByName(runnableProject, profile)
        (settings.configuration as? AspireHostConfiguration)?.updateConfigurationParameters(
            runnableProject,
            projectOutput,
            launchProfile
        )

        return settings
    }

    private fun AspireHostConfiguration.updateConfigurationParameters(
        runnableProject: RunnableProject,
        projectOutput: ProjectOutput?,
        launchProfile: LaunchProfile?
    ) = parameters.apply {
        projectFilePath = runnableProject.projectFilePath
        projectTfm = projectOutput?.tfm?.presentableName ?: ""
        profileName = launchProfile?.name ?: ""
        trackArguments = true
        arguments = getArguments(launchProfile?.content, projectOutput)
        trackWorkingDirectory = true
        workingDirectory = getWorkingDirectory(launchProfile?.content, projectOutput)
        trackEnvs = true
        envs = getEnvironmentVariables(launchProfile?.name, launchProfile?.content)
        trackUrl = true
        startBrowserParameters.apply {
            url = getApplicationUrl(launchProfile?.content)
            startAfterLaunch = launchProfile?.content?.launchBrowser == true
        }
    }

    override fun getHelpTopic() = "me.rafaelldi.aspire.run-config"
}