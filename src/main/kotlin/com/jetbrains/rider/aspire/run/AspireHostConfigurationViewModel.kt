package com.jetbrains.rider.aspire.run

import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.adviseOnce
import com.jetbrains.rider.model.ProjectOutput
import com.jetbrains.rider.model.RunnableProject
import com.jetbrains.rider.model.RunnableProjectsModel
import com.jetbrains.rider.run.configurations.RunnableProjectKinds
import com.jetbrains.rider.run.configurations.controls.*
import com.jetbrains.rider.run.configurations.controls.startBrowser.BrowserSettings
import com.jetbrains.rider.run.configurations.controls.startBrowser.BrowserSettingsEditor
import com.jetbrains.rider.run.configurations.launchSettings.LaunchSettingsJson
import com.jetbrains.rider.run.configurations.project.DotNetStartBrowserParameters
import java.io.File

class AspireHostConfigurationViewModel(
    private val project: Project,
    private val lifetime: Lifetime,
    private val runnableProjectsModel: RunnableProjectsModel?,
    val projectSelector: ProjectSelector,
    val tfmSelector: StringSelector,
    val launchProfileSelector: LaunchProfileSelector,
    val programParametersEditor: ProgramParametersEditor,
    val workingDirectorySelector: PathSelector,
    val environmentVariablesEditor: EnvironmentVariablesEditor,
    val usePodmanRuntimeFlagEditor: FlagEditor,
    separator: ViewSeparator,
    val urlEditor: TextEditor,
    val dotNetBrowserSettingsEditor: BrowserSettingsEditor
) : RunConfigurationViewModelBase() {
    override val controls: List<ControlBase> =
        listOf(
            projectSelector,
            tfmSelector,
            launchProfileSelector,
            programParametersEditor,
            workingDirectorySelector,
            environmentVariablesEditor,
            usePodmanRuntimeFlagEditor,
            separator,
            urlEditor,
            dotNetBrowserSettingsEditor
        )

    private var isLoaded = false

    var trackArguments = true
    var trackWorkingDirectory = true
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

        tfmSelector.string.advise(lifetime) { handleChangeTfmSelection() }
        launchProfileSelector.profile.advise(lifetime) { handleProfileSelection() }
        programParametersEditor.parametersString.advise(lifetime) { handleArgumentsChange() }
        workingDirectorySelector.path.advise(lifetime) { handleWorkingDirectoryChange() }
        environmentVariablesEditor.envs.advise(lifetime) { handleEnvValueChange() }
        urlEditor.text.advise(lifetime) { handleUrlValueChange() }
    }

    private fun handleProjectSelection(runnableProject: RunnableProject) {
        if (!isLoaded) return

        reloadTfmSelector(runnableProject)
        reloadLaunchProfileSelector(runnableProject)

        val projectOutput = getSelectedProjectOutput() ?: return
        val launchProfile = launchProfileSelector.profile.valueOrNull ?: return

        recalculateFields(projectOutput, launchProfile)
    }

    private fun reloadTfmSelector(runnableProject: RunnableProject) {
        val tfms = runnableProject.projectOutputs.map { it.tfm?.presentableName ?: "" }.sorted()
        tfmSelector.stringList.apply {
            clear()
            addAll(tfms)
        }
        if (tfms.any()) {
            tfmSelector.string.set(tfms.first())
        }
    }

    private fun reloadLaunchProfileSelector(runnableProject: RunnableProject) {
        val launchProfiles = FunctionLaunchProfilesService.getInstance(project).getLaunchProfiles(runnableProject)
        launchProfileSelector.profileList.apply {
            clear()
            addAll(launchProfiles)
        }
        if (launchProfiles.any()) {
            launchProfileSelector.profile.set(launchProfiles.first())
        }
    }

    private fun handleChangeTfmSelection() {
        if (!isLoaded) return

        val projectOutput = getSelectedProjectOutput() ?: return
        val launchProfile = launchProfileSelector.profile.valueOrNull ?: return

        recalculateFields(projectOutput, launchProfile)
    }

    private fun handleProfileSelection() {
        if (!isLoaded) return

        val projectOutput = getSelectedProjectOutput() ?: return
        val launchProfile = launchProfileSelector.profile.valueOrNull ?: return

        recalculateFields(projectOutput, launchProfile)
    }

    private fun recalculateFields(projectOutput: ProjectOutput, profile: LaunchProfile) {
        if (trackWorkingDirectory) {
            val workingDirectory = getWorkingDirectory(profile.content, projectOutput)
            workingDirectorySelector.path.set(workingDirectory)
            workingDirectorySelector.defaultValue.set(workingDirectory)
        }
        if (trackArguments) {
            val arguments = getArguments(profile.content, projectOutput)
            programParametersEditor.parametersString.set(arguments)
            programParametersEditor.defaultValue.set(arguments)
        }
        if (trackEnvs) {
            val envs = getEnvironmentVariables(profile.name, profile.content).toSortedMap()
            environmentVariablesEditor.envs.set(envs)
        }
        if (trackUrl) {
            val applicationUrl = getApplicationUrl(profile.content)
            urlEditor.text.value = applicationUrl
            urlEditor.defaultValue.value = applicationUrl
            dotNetBrowserSettingsEditor.settings.value = BrowserSettings(profile.content.launchBrowser, false, null)
        }
    }

    private fun handleArgumentsChange() {
        if (!isLoaded) return

        val projectOutput = getSelectedProjectOutput()
        val launchProfile = launchProfileSelector.profile.valueOrNull
        if (projectOutput == null || launchProfile == null) {
            trackArguments = false
            return
        }

        val arguments = getArguments(launchProfile.content, projectOutput)
        trackArguments = arguments == programParametersEditor.parametersString.value
    }

    private fun handleWorkingDirectoryChange() {
        if (!isLoaded) return

        val projectOutput = getSelectedProjectOutput()
        val launchProfile = launchProfileSelector.profile.valueOrNull
        if (projectOutput == null || launchProfile == null) {
            trackWorkingDirectory = false
            return
        }

        val workingDirectory = getWorkingDirectory(launchProfile.content, projectOutput)
        trackWorkingDirectory = workingDirectory == workingDirectorySelector.path.value
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
        projectTfm: String,
        launchProfileName: String,
        trackArguments: Boolean,
        arguments: String,
        trackWorkingDirectory: Boolean,
        workingDirectory: String,
        trackEnvs: Boolean,
        envs: Map<String, String>,
        usePodmanRuntime: Boolean,
        trackUrl: Boolean,
        dotNetStartBrowserParameters: DotNetStartBrowserParameters
    ) {
        isLoaded = false

        this.trackArguments = trackArguments
        this.trackWorkingDirectory = trackWorkingDirectory
        this.trackEnvs = trackEnvs
        this.trackUrl = trackUrl

        runnableProjectsModel?.projects?.adviseOnce(lifetime) { projectList ->
            usePodmanRuntimeFlagEditor.isSelected.set(usePodmanRuntime)

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
                    addFirstFunctionProject(projectList)
                } else {
                    addFakeProject(projectList, projectFilePath)
                }
            } else {
                addSelectedAspireHostProject(
                    projectList,
                    projectFilePath,
                    projectTfm,
                    launchProfileName,
                    trackArguments,
                    arguments,
                    trackWorkingDirectory,
                    workingDirectory,
                    trackEnvs,
                    envs,
                    trackUrl,
                    dotNetStartBrowserParameters
                )
            }

            isLoaded = true
        }
    }

    private fun addFirstFunctionProject(projectList: List<RunnableProject>) {
        val runnableProject = projectList.firstOrNull { it.kind == AspireRunnableProjectKinds.AspireHost }
            ?: return
        projectSelector.project.set(runnableProject)
        isLoaded = true
        handleProjectSelection(runnableProject)
    }

    private fun addFakeProject(projectList: List<RunnableProject>, projectFilePath: String) {
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

    private fun addSelectedAspireHostProject(
        projectList: List<RunnableProject>,
        projectFilePath: String,
        tfm: String,
        launchProfile: String,
        trackArguments: Boolean,
        arguments: String,
        trackWorkingDirectory: Boolean,
        workingDirectory: String,
        trackEnvs: Boolean,
        envs: Map<String, String>,
        trackUrl: Boolean,
        dotNetStartBrowserParameters: DotNetStartBrowserParameters
    ) {
        val runnableProject = projectList.singleOrNull {
            it.projectFilePath == projectFilePath && it.kind == AspireRunnableProjectKinds.AspireHost
        } ?: return

        projectSelector.project.set(runnableProject)
        reloadTfmSelector(runnableProject)
        reloadLaunchProfileSelector(runnableProject)

        val selectedTfm =
            if (tfm.isNotEmpty()) tfmSelector.stringList.firstOrNull { it == tfm }
            else tfmSelector.stringList.firstOrNull()
        if (selectedTfm != null) {
            tfmSelector.string.set(selectedTfm)
        } else {
            tfmSelector.string.set("")
        }

        val selectedProfile =
            if (launchProfile.isNotEmpty()) launchProfileSelector.profileList.firstOrNull { it.name == launchProfile }
            else launchProfileSelector.profileList.firstOrNull()
        if (selectedProfile != null) {
            launchProfileSelector.profile.set(selectedProfile)
        } else {
            val fakeLaunchProfile = LaunchProfile(launchProfile, LaunchSettingsJson.Profile.UNKNOWN)
            launchProfileSelector.profileList.add(fakeLaunchProfile)
            launchProfileSelector.profile.set(fakeLaunchProfile)
        }

        if (selectedTfm != null && selectedProfile != null) {
            val selectedOutput = getSelectedProjectOutput() ?: return

            val effectiveArguments =
                if (trackArguments) getArguments(selectedProfile.content, selectedOutput)
                else arguments
            programParametersEditor.defaultValue.set(effectiveArguments)
            programParametersEditor.parametersString.set(effectiveArguments)

            val effectiveWorkingDirectory =
                if (trackWorkingDirectory) getWorkingDirectory(selectedProfile.content, selectedOutput)
                else workingDirectory
            workingDirectorySelector.defaultValue.set(effectiveWorkingDirectory)
            workingDirectorySelector.path.set(effectiveWorkingDirectory)

            val effectiveEnvs =
                if (trackEnvs) getEnvironmentVariables(selectedProfile.name, selectedProfile.content)
                else envs
            environmentVariablesEditor.envs.set(effectiveEnvs)

            val effectiveUrl =
                if (trackUrl) getApplicationUrl(selectedProfile.content)
                else dotNetStartBrowserParameters.url
            urlEditor.defaultValue.value = effectiveUrl
            urlEditor.text.value = effectiveUrl
        }
    }

    private fun getSelectedProjectOutput(): ProjectOutput? {
        val selectedProject = projectSelector.project.valueOrNull ?: return null
        return selectedProject
            .projectOutputs
            .singleOrNull { it.tfm?.presentableName == tfmSelector.string.valueOrNull }
    }
}