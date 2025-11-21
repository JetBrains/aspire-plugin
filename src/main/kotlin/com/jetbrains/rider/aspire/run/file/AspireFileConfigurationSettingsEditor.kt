package com.jetbrains.rider.aspire.run.file

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.aspire.AspireBundle
import com.jetbrains.rider.run.configurations.ProtocolLifetimedSettingsEditor
import com.jetbrains.rider.run.configurations.controls.ControlViewBuilder
import com.jetbrains.rider.run.configurations.controls.EnvironmentVariablesEditor
import com.jetbrains.rider.run.configurations.controls.FlagEditor
import com.jetbrains.rider.run.configurations.controls.PathSelector
import com.jetbrains.rider.run.configurations.controls.ProgramParametersEditor
import com.jetbrains.rider.run.configurations.controls.TextEditor
import com.jetbrains.rider.run.configurations.controls.ViewSeparator
import com.jetbrains.rider.run.configurations.controls.startBrowser.BrowserSettingsEditor
import javax.swing.JComponent

internal class AspireFileConfigurationSettingsEditor(private val project: Project) :
    ProtocolLifetimedSettingsEditor<AspireFileConfiguration>() {
    private lateinit var viewModel: AspireFileConfigurationViewModel

    override fun createEditor(lifetime: Lifetime): JComponent {
        viewModel = AspireFileConfigurationViewModel(
            project,
            lifetime,
            PathSelector(
                AspireBundle.message("run.editor.file"),
                "File_path",
                FileChooserDescriptorFactory.singleFile().withExtensionFilter("cs"),
                lifetime
            ),
            ProgramParametersEditor(
                AspireBundle.message("run.editor.arguments"),
                "Program_arguments",
                lifetime
            ),
            PathSelector(
                AspireBundle.message("run.editor.working.directory"),
                "Working_directory",
                FileChooserDescriptorFactory.singleDir(),
                lifetime
            ),
            EnvironmentVariablesEditor(
                AspireBundle.message("run.editor.environment.variables"),
                "Environment_variables"
            ),
            FlagEditor(
                AspireBundle.message("run.editor.podman.runtime"),
                "Use_podman_runtime"
            ),
            ViewSeparator(AspireBundle.message("run.editor.open.browser")),
            TextEditor(AspireBundle.message("run.editor.url"), "URL", lifetime),
            BrowserSettingsEditor("")
        )
        return ControlViewBuilder(lifetime, project, "AspireFile").build(viewModel)
    }

    override fun applyEditorTo(configuration: AspireFileConfiguration) {
        configuration.parameters.apply {
            filePath = FileUtil.toSystemIndependentName(viewModel.filePathSelector.path.value)
            arguments = viewModel.programParametersEditor.parametersString.value
            workingDirectory = FileUtil.toSystemIndependentName(viewModel.workingDirectorySelector.path.value)
            envs = viewModel.environmentVariablesEditor.envs.value
            usePodmanRuntime = viewModel.usePodmanRuntimeFlagEditor.isSelected.value
            startBrowserParameters.url = viewModel.urlEditor.text.value
            startBrowserParameters.browser = viewModel.dotNetBrowserSettingsEditor.settings.value.myBrowser
            startBrowserParameters.startAfterLaunch =
                viewModel.dotNetBrowserSettingsEditor.settings.value.startAfterLaunch
            startBrowserParameters.withJavaScriptDebugger =
                viewModel.dotNetBrowserSettingsEditor.settings.value.withJavaScriptDebugger

        }
    }

    override fun resetEditorFrom(configuration: AspireFileConfiguration) {
        with(configuration.parameters) {
            viewModel.reset(
                filePath,
                arguments,
                workingDirectory,
                envs,
                usePodmanRuntime,
                startBrowserParameters
            )
        }
    }
}