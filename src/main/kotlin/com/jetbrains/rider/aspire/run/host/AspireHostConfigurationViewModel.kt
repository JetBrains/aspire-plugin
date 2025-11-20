package com.jetbrains.rider.aspire.run.host

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.SequentialLifetimes
import com.jetbrains.rd.util.threading.coroutines.launch
import com.jetbrains.rd.util.threading.coroutines.nextNotNullValue
import com.jetbrains.rider.aspire.launchProfiles.getApplicationUrl
import com.jetbrains.rider.aspire.launchProfiles.getArguments
import com.jetbrains.rider.aspire.launchProfiles.getEnvironmentVariables
import com.jetbrains.rider.aspire.launchProfiles.getLaunchBrowserFlag
import com.jetbrains.rider.aspire.launchProfiles.getProjectLaunchProfiles
import com.jetbrains.rider.aspire.launchProfiles.getWorkingDirectory
import com.jetbrains.rider.aspire.run.AspireRunnableProjectKinds
import com.jetbrains.rider.model.ProjectOutput
import com.jetbrains.rider.model.RunnableProject
import com.jetbrains.rider.model.RunnableProjectsModel
import com.jetbrains.rider.run.configurations.RunnableProjectKinds
import com.jetbrains.rider.run.configurations.controls.ControlBase
import com.jetbrains.rider.run.configurations.controls.EnvironmentVariablesEditor
import com.jetbrains.rider.run.configurations.controls.FlagEditor
import com.jetbrains.rider.run.configurations.controls.LaunchProfile
import com.jetbrains.rider.run.configurations.controls.LaunchProfileSelector
import com.jetbrains.rider.run.configurations.controls.PathSelector
import com.jetbrains.rider.run.configurations.controls.ProgramParametersEditor
import com.jetbrains.rider.run.configurations.controls.ProjectSelector
import com.jetbrains.rider.run.configurations.controls.RunConfigurationViewModelBase
import com.jetbrains.rider.run.configurations.controls.StringSelector
import com.jetbrains.rider.run.configurations.controls.TextEditor
import com.jetbrains.rider.run.configurations.controls.ViewSeparator
import com.jetbrains.rider.run.configurations.controls.bindTo
import com.jetbrains.rider.run.configurations.controls.startBrowser.BrowserSettings
import com.jetbrains.rider.run.configurations.controls.startBrowser.BrowserSettingsEditor
import com.jetbrains.rider.run.configurations.launchSettings.LaunchSettingsJson
import com.jetbrains.rider.run.configurations.launchSettings.LaunchSettingsJsonService
import com.jetbrains.rider.run.configurations.project.DotNetStartBrowserParameters
import kotlinx.coroutines.Dispatchers
import java.io.File

class AspireHostConfigurationViewModel(
    private val project: Project,
    lifetime: Lifetime,
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

    private val currentEditSessionLifetimeSource = SequentialLifetimes(lifetime)
    private var currentEditSessionLifetime = currentEditSessionLifetimeSource.next()

    private var isLoaded = false

    var trackArguments = true
    var trackWorkingDirectory = true
    var trackEnvs = true
    var trackUrl = true
    var trackBrowserLaunch = true

    init {
        disable()

        if (runnableProjectsModel != null) {
            val projectModelLifetimes = SequentialLifetimes(lifetime)
            projectSelector.bindTo(
                runnableProjectsModel,
                lifetime,
                { p -> p.kind == AspireRunnableProjectKinds.AspireHost },
                ::enable,
                {
                    projectModelLifetimes.next().launch(Dispatchers.EDT + ModalityState.current().asContextElement()) {
                        handleProjectSelection(it)
                    }
                }
            )
        }

        tfmSelector.string.advise(lifetime) { recalculateFields() }
        launchProfileSelector.profile.advise(lifetime) { recalculateFields() }
        programParametersEditor.parametersString.advise(lifetime) { handleArgumentsChange() }
        workingDirectorySelector.path.advise(lifetime) { handleWorkingDirectoryChange() }
        environmentVariablesEditor.envs.advise(lifetime) { handleEnvValueChange() }
        urlEditor.text.advise(lifetime) { handleUrlValueChange() }
        dotNetBrowserSettingsEditor.settings.advise(lifetime) { handleBrowserSettingsChange() }
    }

    private suspend fun handleProjectSelection(runnableProject: RunnableProject) {
        if (!isLoaded) return

        reloadTfmSelector(runnableProject)
        reloadLaunchProfileSelector(runnableProject)
        recalculateFields()
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

    private suspend fun reloadLaunchProfileSelector(runnableProject: RunnableProject) {
        launchProfileSelector.isLoading.set(true)

        val launchProfiles = LaunchSettingsJsonService.Companion
            .getInstance(project)
            .getProjectLaunchProfiles(runnableProject)
        launchProfileSelector.profileList.apply {
            clear()
            addAll(launchProfiles)
        }
        if (launchProfiles.any()) {
            launchProfileSelector.profile.set(launchProfiles.first())
        }

        launchProfileSelector.isLoading.set(false)
    }

    private fun recalculateFields() {
        if (!isLoaded) return

        val projectOutput = getSelectedProjectOutput() ?: return
        val profile = launchProfileSelector.profile.valueOrNull ?: return

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
            urlEditor.text.set(applicationUrl)
            urlEditor.defaultValue.set(applicationUrl)
        }
        if (trackBrowserLaunch) {
            val launchBrowser = getLaunchBrowserFlag(profile.content)
            val currentSettings = dotNetBrowserSettingsEditor.settings.value
            val browserSettings = BrowserSettings(
                launchBrowser,
                currentSettings.withJavaScriptDebugger,
                currentSettings.myBrowser
            )
            dotNetBrowserSettingsEditor.settings.set(browserSettings)
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

    private fun handleBrowserSettingsChange() {
        if (!isLoaded) return

        val launchProfile = launchProfileSelector.profile.valueOrNull
        if (launchProfile == null) {
            trackBrowserLaunch = false
            return
        }

        val launchBrowser = getLaunchBrowserFlag(launchProfile.content)
        trackBrowserLaunch = dotNetBrowserSettingsEditor.settings.value.startAfterLaunch == launchBrowser
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
        trackBrowserLaunch: Boolean,
        dotNetStartBrowserParameters: DotNetStartBrowserParameters
    ) {
        isLoaded = false
        currentEditSessionLifetime = currentEditSessionLifetimeSource.next()

        this.trackArguments = trackArguments
        this.trackWorkingDirectory = trackWorkingDirectory
        this.trackEnvs = trackEnvs
        this.trackUrl = trackUrl
        this.trackBrowserLaunch = trackBrowserLaunch

        currentEditSessionLifetime.launch(Dispatchers.EDT + ModalityState.current().asContextElement()) {
            val projectList = runnableProjectsModel
                ?.projects
                ?.nextNotNullValue()
                ?.filter { it.kind == AspireRunnableProjectKinds.AspireHost }
                ?: return@launch

            usePodmanRuntimeFlagEditor.isSelected.set(usePodmanRuntime)

            if (projectFilePath.isEmpty() || projectList.none { it.projectFilePath == projectFilePath }) {
                dotNetBrowserSettingsEditor.settings.set(
                    BrowserSettings(
                        dotNetStartBrowserParameters.startAfterLaunch,
                        dotNetStartBrowserParameters.withJavaScriptDebugger,
                        dotNetStartBrowserParameters.browser
                    )
                )

                if (projectFilePath.isEmpty()) {
                    addFirstAspireProject(projectList)
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
                    trackBrowserLaunch,
                    dotNetStartBrowserParameters
                )
            }

            isLoaded = true
        }
    }

    private suspend fun addFirstAspireProject(projectList: List<RunnableProject>) {
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

    private suspend fun addSelectedAspireHostProject(
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
        trackBrowserLaunch: Boolean,
        dotNetStartBrowserParameters: DotNetStartBrowserParameters
    ) {
        val selectedProject = projectList.singleOrNull {
            it.projectFilePath == projectFilePath && it.kind == AspireRunnableProjectKinds.AspireHost
        } ?: return

        projectSelector.project.set(selectedProject)
        reloadTfmSelector(selectedProject)
        reloadLaunchProfileSelector(selectedProject)

        val selectedTfm =
            if (tfm.isNotEmpty())
                tfmSelector.stringList.firstOrNull { it == tfm }
                    ?: tfmSelector.stringList.firstOrNull()
            else
                tfmSelector.stringList.firstOrNull()
        if (selectedTfm != null) {
            tfmSelector.string.set(selectedTfm)
        } else {
            tfmSelector.stringList.add("Unknown")
            tfmSelector.string.set("Unknown")
        }

        val selectedProfile =
            if (launchProfile.isNotEmpty())
                launchProfileSelector.profileList.firstOrNull { it.name == launchProfile }
                    ?: launchProfileSelector.profileList.firstOrNull()
            else
                launchProfileSelector.profileList.firstOrNull()
        if (selectedProfile != null) {
            launchProfileSelector.profile.set(selectedProfile)
        } else {
            val fakeLaunchProfile = LaunchProfile(launchProfile, LaunchSettingsJson.Profile.createUnknown())
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
            urlEditor.defaultValue.set(effectiveUrl)
            urlEditor.text.set(effectiveUrl)

            val effectiveLaunchBrowser =
                if (trackBrowserLaunch) getLaunchBrowserFlag(selectedProfile.content)
                else dotNetStartBrowserParameters.startAfterLaunch
            val browserSettings = BrowserSettings(
                effectiveLaunchBrowser,
                dotNetStartBrowserParameters.withJavaScriptDebugger,
                dotNetStartBrowserParameters.browser
            )
            dotNetBrowserSettingsEditor.settings.set(browserSettings)
        }
    }


    private fun getSelectedProjectOutput(): ProjectOutput? {
        val selectedProject = projectSelector.project.valueOrNull ?: return null
        return selectedProject
            .projectOutputs
            .singleOrNull { it.tfm?.presentableName == tfmSelector.string.valueOrNull }
    }
}