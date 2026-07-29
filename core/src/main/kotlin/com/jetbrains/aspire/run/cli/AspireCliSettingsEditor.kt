package com.jetbrains.aspire.run.cli

import com.intellij.execution.ui.CommonParameterFragments
import com.intellij.execution.ui.RunConfigurationFragmentedEditor
import com.intellij.execution.ui.SettingsEditorFragment
import com.intellij.execution.ui.SettingsEditorFragmentType
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.LabeledComponent
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.Computable
import com.intellij.ui.components.JBTextField
import com.jetbrains.aspire.AspireCoreBundle
import java.awt.BorderLayout
import javax.swing.JComponent

internal class AspireCliSettingsEditor(
    configuration: AspireCliRunConfiguration
) : RunConfigurationFragmentedEditor<AspireCliRunConfiguration>(configuration) {

    override fun createRunFragments(): MutableList<SettingsEditorFragment<AspireCliRunConfiguration, *>> {
        val project = project
        val fragments = mutableListOf<SettingsEditorFragment<AspireCliRunConfiguration, *>>()

        fragments.add(CommonParameterFragments.createRunHeader())

        fragments.add(appHostFileFragment(project))

        val parameterFragments = CommonParameterFragments<AspireCliRunConfiguration>(project, Computable { null })
        fragments.add(parameterFragments.programArguments())
        fragments.add(CommonParameterFragments.createWorkingDirectory(project, Computable { null }))
        fragments.add(CommonParameterFragments.createEnvParameters())

        fragments.add(aspireCliPathFragment(project))
        fragments.add(browserUrlFragment())
        fragments.add(startBrowserTag())
        fragments.add(debugTag())

        return fragments
    }

    private fun appHostFileFragment(project: Project): SettingsEditorFragment<AspireCliRunConfiguration, LabeledComponent<TextFieldWithBrowseButton>> {
        val field = TextFieldWithBrowseButton()
        field.addBrowseFolderListener(
            project,
            FileChooserDescriptorFactory.createSingleFileDescriptor()
                .withTitle(AspireCoreBundle.message("run.editor.cli.app.host.file.title"))
        )
        val component = labeled(field, AspireCoreBundle.message("run.editor.cli.app.host.file"))

        val fragment = SettingsEditorFragment(
            "aspire.cli.app.host.file",
            AspireCoreBundle.message("run.editor.cli.app.host.file.name"),
            null,
            component,
            SettingsEditorFragmentType.COMMAND_LINE,
            { config: AspireCliRunConfiguration, c: LabeledComponent<TextFieldWithBrowseButton> ->
                c.component.text = config.appHostFilePath.orEmpty()
            },
            { config: AspireCliRunConfiguration, c: LabeledComponent<TextFieldWithBrowseButton> ->
                config.appHostFilePath = c.component.text.takeIf { it.isNotBlank() }
            },
            { true }
        )
        fragment.setRemovable(false)
        return fragment
    }

    private fun aspireCliPathFragment(project: Project): SettingsEditorFragment<AspireCliRunConfiguration, LabeledComponent<TextFieldWithBrowseButton>> {
        val field = TextFieldWithBrowseButton()
        field.addBrowseFolderListener(
            project,
            FileChooserDescriptorFactory.createSingleFileDescriptor()
                .withTitle(AspireCoreBundle.message("run.editor.cli.path.title"))
        )
        val component = labeled(field, AspireCoreBundle.message("run.editor.cli.path"))

        return SettingsEditorFragment(
            "aspire.cli.path",
            AspireCoreBundle.message("run.editor.cli.path.name"),
            null,
            component,
            SettingsEditorFragmentType.EDITOR,
            { config: AspireCliRunConfiguration, c: LabeledComponent<TextFieldWithBrowseButton> ->
                c.component.text = config.aspireCliPath.orEmpty()
            },
            { config: AspireCliRunConfiguration, c: LabeledComponent<TextFieldWithBrowseButton> ->
                config.aspireCliPath = c.component.text.takeIf { it.isNotBlank() }
            },
            { config: AspireCliRunConfiguration -> !config.aspireCliPath.isNullOrBlank() }
        )
    }

    private fun browserUrlFragment(): SettingsEditorFragment<AspireCliRunConfiguration, LabeledComponent<JBTextField>> {
        val field = JBTextField()
        val component = labeled(field, AspireCoreBundle.message("run.editor.cli.browser.url"))

        return SettingsEditorFragment(
            "aspire.cli.browser.url",
            AspireCoreBundle.message("run.editor.cli.browser.url.name"),
            null,
            component,
            SettingsEditorFragmentType.EDITOR,
            { config: AspireCliRunConfiguration, c: LabeledComponent<JBTextField> ->
                c.component.text = config.browserUrl.orEmpty()
            },
            { config: AspireCliRunConfiguration, c: LabeledComponent<JBTextField> ->
                config.browserUrl = c.component.text.takeIf { it.isNotBlank() }
            },
            { config: AspireCliRunConfiguration -> !config.browserUrl.isNullOrBlank() }
        )
    }

    private fun startBrowserTag(): SettingsEditorFragment<AspireCliRunConfiguration, *> =
        SettingsEditorFragment.createTag(
            "aspire.cli.start.browser",
            AspireCoreBundle.message("run.editor.cli.start.browser"),
            null,
            { config: AspireCliRunConfiguration -> config.startBrowserAfterLaunch },
            { config: AspireCliRunConfiguration, value: Boolean -> config.startBrowserAfterLaunch = value }
        )

    private fun debugTag(): SettingsEditorFragment<AspireCliRunConfiguration, *> =
        SettingsEditorFragment.createTag(
            "aspire.cli.enable.debugging",
            AspireCoreBundle.message("run.editor.cli.enable.debugging"),
            null,
            { config: AspireCliRunConfiguration -> config.enableIdeDebugging },
            { config: AspireCliRunConfiguration, value: Boolean -> config.enableIdeDebugging = value }
        )

    private fun <T : JComponent> labeled(component: T, label: String): LabeledComponent<T> =
        LabeledComponent.create(component, label).apply { labelLocation = BorderLayout.WEST }
}
