package com.jetbrains.aspire.rider.run.file

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.toNioPathOrNull
import com.jetbrains.aspire.rider.launchProfiles.*
import com.jetbrains.aspire.rider.run.AspireRunnableProjectKinds
import com.jetbrains.rd.ide.model.RdFileBasedProgramSource
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.SequentialLifetimes
import com.jetbrains.rd.util.threading.coroutines.launch
import com.jetbrains.rider.ijent.extensions.toRd
import com.jetbrains.rider.model.ProjectOutput
import com.jetbrains.rider.model.runnableProjectsModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.run.configurations.controls.*
import com.jetbrains.rider.run.configurations.controls.startBrowser.BrowserSettings
import com.jetbrains.rider.run.configurations.controls.startBrowser.BrowserSettingsEditor
import com.jetbrains.rider.run.configurations.dotNetFile.FileBasedProgramProjectManager
import com.jetbrains.rider.run.configurations.launchSettings.LaunchSettingsJson
import com.jetbrains.rider.run.configurations.launchSettings.LaunchSettingsJsonService
import com.jetbrains.rider.run.configurations.project.DotNetStartBrowserParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.exists

internal class AspireFileConfigurationViewModel(
    private val project: Project,
    lifetime: Lifetime,
    val filePathSelector: PathSelector,
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
            filePathSelector,
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
        filePathSelector.path.advise(lifetime) {
            currentEditSessionLifetime.launch(Dispatchers.UI + ModalityState.current().asContextElement()) {
                handleFilePathSelection(it)
            }
        }

        launchProfileSelector.profile.advise(lifetime) {
            if (!isLoaded) return@advise
            currentEditSessionLifetime.launch(Dispatchers.UI + ModalityState.current().asContextElement()) {
                recalculateFields()
            }
        }

        programParametersEditor.parametersString.advise(lifetime) {
            currentEditSessionLifetime.launch(Dispatchers.UI + ModalityState.current().asContextElement()) {
                if (!isLoaded) return@launch
                handleArgumentsChange()
            }
        }
        workingDirectorySelector.path.advise(lifetime) { handleWorkingDirectoryChange() }
        environmentVariablesEditor.envs.advise(lifetime) { handleEnvValueChange() }
        urlEditor.text.advise(lifetime) { handleUrlValueChange() }
        dotNetBrowserSettingsEditor.settings.advise(lifetime) { handleBrowserSettingsChange() }
    }

    private suspend fun handleFilePathSelection(filePath: String) {
        reloadLaunchProfileSelector(filePath)
        isLoaded = true
        recalculateFields()
    }

    private suspend fun reloadLaunchProfileSelector(filePath: String) {
        launchProfileSelector.isLoading.set(true)
        val path = filePath.toNioPathOrNull()
        val launchSettingsPath = path?.let { getLaunchSettingsPathForCsFile(it) }

        if (launchSettingsPath == null || !launchSettingsPath.exists()) {
            launchProfileSelector.profileList.clear()
            launchProfileSelector.isLoading.set(false)
            return
        }

        val profiles = LaunchSettingsJsonService.getInstance(project).getProjectLaunchProfiles(launchSettingsPath)

        launchProfileSelector.profileList.apply {
            clear()
            addAll(profiles)
        }

        if (profiles.any()) {
            launchProfileSelector.profile.set(profiles.first())
        }
        launchProfileSelector.isLoading.set(false)
    }

    private suspend fun recalculateFields(profile: LaunchProfile? = launchProfileSelector.profile.valueOrNull) {
        val csFile = filePathSelector.path.value.toNioPathOrNull()
        val projectOutput = csFile?.let { tryGetProjectOutputForCsFile(it) }

        if (trackArguments) {
            val arguments = getArguments(profile?.content, projectOutput)
            programParametersEditor.parametersString.set(arguments)
            programParametersEditor.defaultValue.set(arguments)
        }

        if (trackWorkingDirectory) {
            // `dotnet run --file` uses the `.cs` file parent directory as the default working directory.
            val workingDirectory = profile?.content?.workingDirectory ?: csFile?.parent?.toString() ?: ""
            workingDirectorySelector.path.set(workingDirectory)
            workingDirectorySelector.defaultValue.set(workingDirectory)
        }

        if (trackEnvs) {
            val envs = getEnvironmentVariables(profile?.name, profile?.content).toSortedMap()
            environmentVariablesEditor.envs.set(envs)
        }

        if (trackUrl) {
            val applicationUrl = getApplicationUrl(profile?.content)
            urlEditor.text.set(applicationUrl)
            urlEditor.defaultValue.set(applicationUrl)
        }

        if (trackBrowserLaunch) {
            val launchBrowser = getLaunchBrowserFlag(profile?.content)
            val currentSettings = dotNetBrowserSettingsEditor.settings.value
            val browserSettings = BrowserSettings(
                launchBrowser,
                currentSettings.withJavaScriptDebugger,
                currentSettings.myBrowser
            )
            dotNetBrowserSettingsEditor.settings.set(browserSettings)
        }
    }

    private suspend fun handleArgumentsChange() {
        if (!isLoaded) return

        val projectOutput = filePathSelector.path.value.toNioPathOrNull()?.let { tryGetProjectOutputForCsFile(it) }
        val profile = launchProfileSelector.profile.valueOrNull

        if (projectOutput == null || profile == null) {
            trackArguments = false
            return
        }

        val arguments = getArguments(profile.content, projectOutput)
        trackArguments = programParametersEditor.parametersString.value == arguments
    }

    private fun handleWorkingDirectoryChange() {
        if (!isLoaded) return
        val profile = launchProfileSelector.profile.valueOrNull
        val profileWorkingDirectory = getWorkingDirectory(profile?.content, projectProperties = null)
        trackWorkingDirectory = workingDirectorySelector.path.value == profileWorkingDirectory
    }

    private fun handleEnvValueChange() {
        if (!isLoaded) return
        val profile = launchProfileSelector.profile.valueOrNull
        val profileEnvs = getEnvironmentVariables(profile?.name, profile?.content)
        trackEnvs = environmentVariablesEditor.envs.value == profileEnvs
    }

    private fun handleUrlValueChange() {
        if (!isLoaded) return
        val profile = launchProfileSelector.profile.valueOrNull
        val profileUrl = getApplicationUrl(profile?.content)
        trackUrl = urlEditor.text.value == profileUrl
    }

    private fun handleBrowserSettingsChange() {
        if (!isLoaded) return
        val profile = launchProfileSelector.profile.valueOrNull
        val profileLaunchBrowser = getLaunchBrowserFlag(profile?.content)
        trackBrowserLaunch = dotNetBrowserSettingsEditor.settings.value.startAfterLaunch == profileLaunchBrowser
    }

    fun reset(
        filePath: String,
        profileName: String,
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

        this.trackArguments = trackArguments
        this.trackWorkingDirectory = trackWorkingDirectory
        this.trackEnvs = trackEnvs
        this.trackUrl = trackUrl
        this.trackBrowserLaunch = trackBrowserLaunch

        filePathSelector.path.set(filePath)

        currentEditSessionLifetime = currentEditSessionLifetimeSource.next()
        currentEditSessionLifetime.launch(Dispatchers.UI + ModalityState.current().asContextElement()) {
            reloadLaunchProfileSelector(filePath)

            val selectedProfile = launchProfileSelector.profileList.firstOrNull { it.name == profileName }
                ?: launchProfileSelector.profileList.firstOrNull()

            if (selectedProfile != null) {
                launchProfileSelector.profile.set(selectedProfile)
            } else {
                val fakeLaunchProfile = LaunchProfile(profileName, LaunchSettingsJson.Profile.createUnknown())
                launchProfileSelector.profileList.add(fakeLaunchProfile)
                launchProfileSelector.profile.set(fakeLaunchProfile)
            }

            val csFile = filePath.toNioPathOrNull() ?: return@launch

            val projectOutput = tryGetProjectOutputForCsFile(csFile)

            val defaultWorkingDirectory = selectedProfile?.content?.workingDirectory ?: csFile.parent?.toString() ?: ""

            val effectiveArguments = if(trackArguments) getArguments(selectedProfile?.content, projectOutput)
            else arguments
            programParametersEditor.defaultValue.set(effectiveArguments)
            programParametersEditor.parametersString.set(effectiveArguments)

            val effectiveWorkingDirectory = if (trackWorkingDirectory) defaultWorkingDirectory else workingDirectory
            workingDirectorySelector.defaultValue.set(effectiveWorkingDirectory)
            workingDirectorySelector.path.set(effectiveWorkingDirectory)

            val effectiveEnvs =
                if (trackEnvs) getEnvironmentVariables(selectedProfile?.name, selectedProfile?.content)
                else envs
            environmentVariablesEditor.envs.set(effectiveEnvs)

            val effectiveUrl =
                if (trackUrl) getApplicationUrl(selectedProfile?.content)
                else dotNetStartBrowserParameters.url
            urlEditor.defaultValue.set(effectiveUrl)
            urlEditor.text.set(effectiveUrl)

            val effectiveLaunchBrowser =
                if (trackBrowserLaunch) getLaunchBrowserFlag(selectedProfile?.content)
                else dotNetStartBrowserParameters.startAfterLaunch
            val browserSettings = BrowserSettings(
                effectiveLaunchBrowser,
                dotNetStartBrowserParameters.withJavaScriptDebugger,
                dotNetStartBrowserParameters.browser
            )
            dotNetBrowserSettingsEditor.settings.set(browserSettings)
            usePodmanRuntimeFlagEditor.isSelected.set(usePodmanRuntime)
            urlEditor.text.set(dotNetStartBrowserParameters.url)
            isLoaded = true
        }
    }

    @Suppress("UnstableApiUsage")
    private suspend fun tryGetProjectOutputForCsFile(csFile: Path): ProjectOutput? {
        val sourceFile = RdFileBasedProgramSource(csFile.toRd())
        val projectManager = FileBasedProgramProjectManager.getInstance(project)

        val fileBasedProjectPath = withContext(Dispatchers.Default) {
            projectManager.createProjectFile(sourceFile, currentEditSessionLifetime)
        } ?: return null

        val runnableProject = project.solution.runnableProjectsModel.projects.valueOrNull?.singleOrNull {
            it.kind == AspireRunnableProjectKinds.AspireHost && it.projectFilePath.toNioPathOrNull() == fileBasedProjectPath
        }

        return runnableProject?.projectOutputs?.singleOrNull()
    }
}