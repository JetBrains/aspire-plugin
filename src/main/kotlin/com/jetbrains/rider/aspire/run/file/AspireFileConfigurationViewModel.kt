package com.jetbrains.rider.aspire.run.file

import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.run.configurations.controls.ControlBase
import com.jetbrains.rider.run.configurations.controls.EnvironmentVariablesEditor
import com.jetbrains.rider.run.configurations.controls.FlagEditor
import com.jetbrains.rider.run.configurations.controls.PathSelector
import com.jetbrains.rider.run.configurations.controls.ProgramParametersEditor
import com.jetbrains.rider.run.configurations.controls.RunConfigurationViewModelBase
import com.jetbrains.rider.run.configurations.controls.TextEditor
import com.jetbrains.rider.run.configurations.controls.ViewSeparator
import com.jetbrains.rider.run.configurations.controls.startBrowser.BrowserSettings
import com.jetbrains.rider.run.configurations.controls.startBrowser.BrowserSettingsEditor
import com.jetbrains.rider.run.configurations.project.DotNetStartBrowserParameters

internal class AspireFileConfigurationViewModel(
    private val project: Project,
    lifetime: Lifetime,
    val filePathSelector: PathSelector,
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
            programParametersEditor,
            workingDirectorySelector,
            environmentVariablesEditor,
            usePodmanRuntimeFlagEditor,
            separator,
            urlEditor,
            dotNetBrowserSettingsEditor
        )

    fun reset(
        filePath: String,
        programParameters: String,
        workingDirectory: String,
        envs: Map<String, String>,
        usePodmanRuntime: Boolean,
        dotNetStartBrowserParameters: DotNetStartBrowserParameters
    ) {
        filePathSelector.path.set(filePath)
        programParametersEditor.parametersString.set(programParameters)
        workingDirectorySelector.path.set(workingDirectory)
        environmentVariablesEditor.envs.set(envs)
        usePodmanRuntimeFlagEditor.isSelected.set(usePodmanRuntime)
        val browserSettings = BrowserSettings(
            dotNetStartBrowserParameters.startAfterLaunch,
            dotNetStartBrowserParameters.withJavaScriptDebugger,
            dotNetStartBrowserParameters.browser
        )
        dotNetBrowserSettingsEditor.settings.set(browserSettings)
    }
}