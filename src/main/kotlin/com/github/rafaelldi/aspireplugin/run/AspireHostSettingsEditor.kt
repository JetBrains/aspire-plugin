package com.github.rafaelldi.aspireplugin.run

import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.run.configurations.ProtocolLifetimedSettingsEditor
import com.jetbrains.rider.run.configurations.controls.ControlViewBuilder
import com.jetbrains.rider.run.configurations.controls.ProjectSelector
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
            lifetime,
            project.runnableProjectsModelIfAvailable,
            ProjectSelector("Project:", "Project"),
            ViewSeparator("Open browser"),
            TextEditor("URL", "URL", lifetime),
            BrowserSettingsEditor("")
        )
        return ControlViewBuilder(lifetime, project, "AspireHost").build(viewModel)
    }

    override fun applyEditorTo(configuration: AspireHostConfiguration) {
        val selectedProject = viewModel.projectSelector.project.valueOrNull
        if (selectedProject != null) {
            configuration.parameters.apply {
                projectFilePath = selectedProject.projectFilePath
                trackUrl = viewModel.trackUrl
                startBrowserParameters.url = viewModel.urlEditor.text.value
                startBrowserParameters.browser = viewModel.dotNetBrowserSettingsEditor.settings.value.myBrowser
                startBrowserParameters.startAfterLaunch =
                    viewModel.dotNetBrowserSettingsEditor.settings.value.startAfterLaunch
                startBrowserParameters.withJavaScriptDebugger =
                    viewModel.dotNetBrowserSettingsEditor.settings.value.withJavaScriptDebugger
            }
        }
    }

    override fun resetEditorFrom(configuration: AspireHostConfiguration) {
        configuration.parameters.apply {
            viewModel.reset(
                projectFilePath,
                trackUrl,
                startBrowserParameters
            )
        }
    }
}