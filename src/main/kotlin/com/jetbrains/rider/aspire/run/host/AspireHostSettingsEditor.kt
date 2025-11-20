package com.jetbrains.rider.aspire.run.host

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.aspire.AspireBundle
import com.jetbrains.rider.run.configurations.ProtocolLifetimedSettingsEditor
import com.jetbrains.rider.run.configurations.controls.ControlViewBuilder
import com.jetbrains.rider.run.configurations.controls.EnvironmentVariablesEditor
import com.jetbrains.rider.run.configurations.controls.FlagEditor
import com.jetbrains.rider.run.configurations.controls.LaunchProfileSelector
import com.jetbrains.rider.run.configurations.controls.PathSelector
import com.jetbrains.rider.run.configurations.controls.ProgramParametersEditor
import com.jetbrains.rider.run.configurations.controls.ProjectSelector
import com.jetbrains.rider.run.configurations.controls.StringSelector
import com.jetbrains.rider.run.configurations.controls.TextEditor
import com.jetbrains.rider.run.configurations.controls.ViewSeparator
import com.jetbrains.rider.run.configurations.controls.startBrowser.BrowserSettingsEditor
import com.jetbrains.rider.run.configurations.runnableProjectsModelIfAvailable
import javax.swing.JComponent

class AspireHostSettingsEditor(private val project: Project) :
    ProtocolLifetimedSettingsEditor<AspireHostConfiguration>() {
    private lateinit var viewModel: AspireHostConfigurationViewModel

    override fun createEditor(lifetime: Lifetime): JComponent {
        viewModel = AspireHostConfigurationViewModel(
            project,
            lifetime,
            project.runnableProjectsModelIfAvailable,
            ProjectSelector(AspireBundle.message("run.editor.project"), "Project"),
            StringSelector("Target framework:", "Target_framework"),
            LaunchProfileSelector(AspireBundle.message("run.editor.launch.profile"), "Launch_profile"),
            ProgramParametersEditor("Arguments:", "Program_arguments", lifetime),
            PathSelector(
                "Working directory:",
                "Working_directory",
                FileChooserDescriptorFactory.createSingleFolderDescriptor(),
                lifetime
            ),
            EnvironmentVariablesEditor(
                AspireBundle.message("run.editor.environment.variables"),
                "Environment_variables"
            ),
            FlagEditor("Use Podman container runtime", "Use_podman_runtime"),
            ViewSeparator(AspireBundle.message("run.editor.open.browser")),
            TextEditor(AspireBundle.message("run.editor.url"), "URL", lifetime),
            BrowserSettingsEditor("")
        )
        return ControlViewBuilder(lifetime, project, "AspireHost").build(viewModel)
    }

    override fun applyEditorTo(configuration: AspireHostConfiguration) {
        configuration.parameters.apply {
            projectFilePath = viewModel.projectSelector.project.valueOrNull?.projectFilePath ?: ""
            projectTfm = viewModel.tfmSelector.string.valueOrNull ?: ""
            profileName = viewModel.launchProfileSelector.profile.valueOrNull?.name ?: ""
            trackArguments = viewModel.trackArguments
            arguments = viewModel.programParametersEditor.parametersString.value
            trackWorkingDirectory = viewModel.trackWorkingDirectory
            workingDirectory = FileUtil.toSystemIndependentName(viewModel.workingDirectorySelector.path.value)
            trackEnvs = viewModel.trackEnvs
            envs = viewModel.environmentVariablesEditor.envs.value
            usePodmanRuntime = viewModel.usePodmanRuntimeFlagEditor.isSelected.value
            trackUrl = viewModel.trackUrl
            trackBrowserLaunch = viewModel.trackBrowserLaunch
            startBrowserParameters.url = viewModel.urlEditor.text.value
            startBrowserParameters.browser = viewModel.dotNetBrowserSettingsEditor.settings.value.myBrowser
            startBrowserParameters.startAfterLaunch =
                viewModel.dotNetBrowserSettingsEditor.settings.value.startAfterLaunch
            startBrowserParameters.withJavaScriptDebugger =
                viewModel.dotNetBrowserSettingsEditor.settings.value.withJavaScriptDebugger
        }
    }

    override fun resetEditorFrom(configuration: AspireHostConfiguration) {
        configuration.parameters.apply {
            viewModel.reset(
                projectFilePath,
                projectTfm,
                profileName,
                trackArguments,
                arguments,
                trackWorkingDirectory,
                workingDirectory,
                trackEnvs,
                envs,
                usePodmanRuntime,
                trackUrl,
                trackBrowserLaunch,
                startBrowserParameters
            )
        }
    }
}