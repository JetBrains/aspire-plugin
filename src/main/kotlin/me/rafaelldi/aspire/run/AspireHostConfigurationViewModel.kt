package me.rafaelldi.aspire.run

import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.adviseOnce
import com.jetbrains.rider.model.RunnableProject
import com.jetbrains.rider.model.RunnableProjectsModel
import com.jetbrains.rider.run.configurations.RunnableProjectKinds
import com.jetbrains.rider.run.configurations.controls.*
import com.jetbrains.rider.run.configurations.controls.startBrowser.BrowserSettings
import com.jetbrains.rider.run.configurations.controls.startBrowser.BrowserSettingsEditor
import com.jetbrains.rider.run.configurations.launchSettings.LaunchSettingsJson
import com.jetbrains.rider.run.configurations.launchSettings.LaunchSettingsJsonService
import com.jetbrains.rider.run.configurations.project.DotNetStartBrowserParameters
import java.io.File

class AspireHostConfigurationViewModel(
    private val lifetime: Lifetime,
    private val runnableProjectsModel: RunnableProjectsModel?,
    val projectSelector: ProjectSelector,
    val launchProfileSelector: LaunchProfileSelector,
    val environmentVariablesEditor: EnvironmentVariablesEditor,
    separator: ViewSeparator,
    val urlEditor: TextEditor,
    val dotNetBrowserSettingsEditor: BrowserSettingsEditor
) : RunConfigurationViewModelBase() {
    override val controls: List<ControlBase> =
        listOf(
            projectSelector,
            launchProfileSelector,
            environmentVariablesEditor,
            separator,
            urlEditor,
            dotNetBrowserSettingsEditor
        )

    private var isLoaded = false

    var trackEnvs = true
    var trackUrl = true

    init {
        disable()

        if (runnableProjectsModel != null) {
            projectSelector.bindTo(
                runnableProjectsModel,
                lifetime,
                { p -> p.kind == AspireRunnableProjectKinds.AspireHost },
                ::enable,
                ::handleProjectSelection
            )
        }

        launchProfileSelector.profile.advise(lifetime) { handleProfileSelection() }
        environmentVariablesEditor.envs.advise(lifetime) { handleEnvValueChange() }
        urlEditor.text.advise(lifetime) { handleUrlValueChange() }
    }

    private fun handleProjectSelection(runnableProject: RunnableProject) {
        if (!isLoaded) return

        val launchProfiles = getLaunchProfiles(runnableProject)
        launchProfileSelector.profileList.apply {
            clear()
            addAll(launchProfiles)
        }
        if (launchProfiles.any()) {
            launchProfileSelector.profile.set(launchProfiles.first())
        }

        handleProfileSelection()
    }

    private fun handleProfileSelection() {
        if (!isLoaded) return

        val launchProfile = launchProfileSelector.profile.valueOrNull ?: return

        val environmentVariables = getEnvironmentVariables(launchProfile.name, launchProfile.content)
        environmentVariablesEditor.envs.set(environmentVariables)

        val applicationUrl = getApplicationUrl(launchProfile.content)
        if (!applicationUrl.isNullOrEmpty()) {
            urlEditor.defaultValue.value = applicationUrl
            urlEditor.text.value = applicationUrl
            dotNetBrowserSettingsEditor.settings.value =
                BrowserSettings(launchProfile.content.launchBrowser, false, null)
        }
    }

    private fun handleEnvValueChange() {
        if (!isLoaded) return

        val launchProfile = launchProfileSelector.profile.valueOrNull
        if (launchProfile == null) {
            trackEnvs = false
            return
        }
        val envs = getEnvironmentVariables(launchProfile.name, launchProfile.content).toSortedMap()
        val editorEnvs = environmentVariablesEditor.envs.value.toSortedMap()
        trackEnvs = envs == editorEnvs
    }

    private fun handleUrlValueChange() {
        if (!isLoaded) return

        val launchProfile = launchProfileSelector.profile.valueOrNull
        if (launchProfile == null) {
            trackUrl = false
            return
        }
        val applicationUrl = getApplicationUrl(launchProfile.content)
        trackUrl = urlEditor.text.value == applicationUrl
    }

    fun reset(
        projectFilePath: String,
        launchProfileName: String,
        trackEnvs: Boolean,
        envs: Map<String, String>,
        trackUrl: Boolean,
        dotNetStartBrowserParameters: DotNetStartBrowserParameters
    ) {
        isLoaded = false

        this.trackEnvs = trackEnvs
        this.trackUrl = trackUrl

        runnableProjectsModel?.projects?.adviseOnce(lifetime) { projectList ->
            dotNetBrowserSettingsEditor.settings.set(
                BrowserSettings(
                    dotNetStartBrowserParameters.startAfterLaunch,
                    dotNetStartBrowserParameters.withJavaScriptDebugger,
                    dotNetStartBrowserParameters.browser
                )
            )

            if (projectFilePath.isEmpty() || projectList.none {
                    it.projectFilePath == projectFilePath && it.kind == AspireRunnableProjectKinds.AspireHost
                }) {
                if (projectFilePath.isEmpty()) {
                    projectList.firstOrNull { it.kind == AspireRunnableProjectKinds.AspireHost }
                        ?.let { runnableProject ->
                            projectSelector.project.set(runnableProject)
                            isLoaded = true
                            handleProjectSelection(runnableProject)
                        }
                } else {
                    val fakeProjectName = File(projectFilePath).name
                    val fakeProject = RunnableProject(
                        fakeProjectName,
                        fakeProjectName,
                        projectFilePath,
                        RunnableProjectKinds.Unloaded,
                        emptyList(),
                        emptyList(),
                        null,
                        emptyList()
                    )
                    projectSelector.projectList.apply {
                        clear()
                        addAll(projectList + fakeProject)
                    }
                    projectSelector.project.set(fakeProject)
                }
            } else {
                projectList.singleOrNull {
                    it.projectFilePath == projectFilePath && it.kind == AspireRunnableProjectKinds.AspireHost
                }?.let { runnableProject ->
                    projectSelector.project.set(runnableProject)

                    val launchProfiles = getLaunchProfiles(runnableProject)
                    launchProfileSelector.profileList.apply {
                        clear()
                        addAll(launchProfiles)
                    }

                    val launchProfile = launchProfileSelector.profileList.firstOrNull { it.name == launchProfileName }
                    if (launchProfile != null) {
                        launchProfileSelector.profile.set(launchProfile)
                    } else {
                        val fakeLaunchProfile = LaunchProfile(launchProfileName, LaunchSettingsJson.Profile.UNKNOWN)
                        launchProfileSelector.profileList.add(fakeLaunchProfile)
                        launchProfileSelector.profile.set(fakeLaunchProfile)
                    }

                    isLoaded = true

                    if (launchProfile != null) {
                        val effectiveEnvs =
                            if (trackEnvs) getEnvironmentVariables(launchProfile.name, launchProfile.content)
                            else envs
                        environmentVariablesEditor.envs.set(effectiveEnvs)

                        val effectiveUrl =
                            if (trackUrl) getApplicationUrl(launchProfile.content)
                            else dotNetStartBrowserParameters.url
                        urlEditor.defaultValue.value = effectiveUrl ?: ""
                        urlEditor.text.value = effectiveUrl ?: ""
                    }
                }
            }

            isLoaded = true
        }
    }

    private fun getLaunchProfiles(runnableProject: RunnableProject): List<LaunchProfile>{
        val launchSettings = LaunchSettingsJsonService.loadLaunchSettings(runnableProject)
        return launchSettings?.profiles
            .orEmpty()
            .asSequence()
            .filter { it.value.commandName.equals("Project", true) }
            .map { (name, content) -> LaunchProfile(name, content) }
            .sortedBy { it.name }
            .toList()
    }
}