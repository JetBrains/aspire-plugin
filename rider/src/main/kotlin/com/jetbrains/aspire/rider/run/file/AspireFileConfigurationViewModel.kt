package com.jetbrains.aspire.rider.run.file

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.util.io.isFile
import com.jetbrains.aspire.rider.launchProfiles.*
import com.jetbrains.rd.ide.model.singleFileProgramModel
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.SequentialLifetimes
import com.jetbrains.rd.util.threading.coroutines.launch
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.run.configurations.controls.*
import com.jetbrains.rider.run.configurations.controls.startBrowser.BrowserSettings
import com.jetbrains.rider.run.configurations.controls.startBrowser.BrowserSettingsEditor
import com.jetbrains.rider.run.configurations.launchSettings.LaunchSettingsJsonService
import com.jetbrains.rider.run.configurations.project.DotNetStartBrowserParameters
import com.jetbrains.rider.run.environment.ExecutableParameterProcessor
import kotlinx.coroutines.Dispatchers
import kotlin.io.path.exists
import kotlin.io.path.nameWithoutExtension

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
            currentEditSessionLifetime.launch(Dispatchers.EDT + ModalityState.current().asContextElement()) {
                handleFilePathSelection(it)
            }
        }

        launchProfileSelector.profile.advise(lifetime) { recalculateFields() }
        programParametersEditor.parametersString.advise(lifetime) { handleArgumentsChange() }
        workingDirectorySelector.path.advise(lifetime) { handleWorkingDirectoryChange() }
        environmentVariablesEditor.envs.advise(lifetime) { handleEnvValueChange() }
        urlEditor.text.advise(lifetime) { handleUrlValueChange() }
        dotNetBrowserSettingsEditor.settings.advise(lifetime) { handleBrowserSettingsChange() }
    }

    private suspend fun handleFilePathSelection(filePath: String) {
        if (!isLoaded) return

        reloadLaunchProfileSelector(filePath)
        recalculateFields()
    }

    private suspend fun reloadLaunchProfileSelector(filePath: String) {
        val path = filePath.toNioPathOrNull()
        val launchSettingsPath = path?.let { getLaunchSettingsPathForCsFile(it) }

        if (launchSettingsPath == null || !launchSettingsPath.exists()) {
            launchProfileSelector.profileList.clear()
            return
        }

        val profiles = LaunchSettingsJsonService.getInstance(project).getLaunchProfiles(launchSettingsPath)

        launchProfileSelector.profileList.apply {
            clear()
            addAll(profiles)
        }

        if (profiles.any()) {
            launchProfileSelector.profile.set(profiles.first())
        }
    }

    private fun recalculateFields() {
        if (!isLoaded) return

        val profile = launchProfileSelector.profile.valueOrNull

        if (trackArguments) {
            val arguments = getArguments(profile?.content, null)
            programParametersEditor.parametersString.set(arguments)
        }

        if (trackWorkingDirectory) {
            val workingDirectory = profile?.content?.workingDirectory ?: "" // TODO: Resolve msbuild properties
            workingDirectorySelector.path.set(workingDirectory)
            workingDirectorySelector.defaultValue.set(workingDirectory)
        }

        if (trackEnvs) {
            val envs = getEnvironmentVariables(profile?.name, profile?.content)
            environmentVariablesEditor.envs.set(envs)
        }

        if (trackUrl) {
            val applicationUrl = getApplicationUrl(profile?.content)
            urlEditor.text.set(applicationUrl)
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

    private fun handleArgumentsChange() {
        if (!isLoaded) return
        val profile = launchProfileSelector.profile.valueOrNull
        val profileArguments = getArguments(profile?.content, null)
        trackArguments = programParametersEditor.parametersString.value == profileArguments
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
        currentEditSessionLifetime.launch(Dispatchers.EDT + ModalityState.current().asContextElement()) {
            reloadLaunchProfileSelector(filePath)

            val profile = launchProfileSelector.profileList.firstOrNull { it.name == profileName }
            if (profile != null) {
                launchProfileSelector.profile.set(profile)
            }

            programParametersEditor.parametersString.set(arguments)
            workingDirectorySelector.path.set(workingDirectory)
            environmentVariablesEditor.envs.set(envs)
            usePodmanRuntimeFlagEditor.isSelected.set(usePodmanRuntime)
            urlEditor.text.set(dotNetStartBrowserParameters.url)

            val browserSettings = BrowserSettings(
                dotNetStartBrowserParameters.startAfterLaunch,
                dotNetStartBrowserParameters.withJavaScriptDebugger,
                dotNetStartBrowserParameters.browser
            )
            dotNetBrowserSettingsEditor.settings.set(browserSettings)

            isLoaded = true
        }
    }
}