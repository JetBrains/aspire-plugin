package com.jetbrains.aspire.run.cli

import com.intellij.execution.ui.CommonParameterFragments
import com.intellij.execution.ui.RunConfigurationFragmentedEditor
import com.intellij.execution.ui.SettingsEditorFragment
import com.intellij.execution.ui.SettingsEditorFragmentType
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.LabeledComponent
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.Computable
import com.intellij.ui.components.JBTextField
import com.jetbrains.aspire.AspireCoreBundle
import org.jetbrains.annotations.Nls
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

        val parameterFragments = CommonParameterFragments<AspireCliRunConfiguration>(project) { null }
        fragments.add(parameterFragments.programArguments())
        fragments.add(CommonParameterFragments.createWorkingDirectory(project, Computable { null }))
        fragments.add(CommonParameterFragments.createEnvParameters())

        fragments.add(aspireCliPathFragment(project))
        fragments.add(browserUrlFragment())
        fragments.add(logLevelFragment())
        fragments.add(startBrowserTag())
        fragments.add(debugTag())
        fragments.add(noBuildTag())
        fragments.add(isolatedTag())
        fragments.add(waitForDebuggerTag())

        return fragments
    }

    private fun appHostFileFragment(project: Project): SettingsEditorFragment<AspireCliRunConfiguration, LabeledComponent<TextFieldWithBrowseButton>> {
        val field = TextFieldWithBrowseButton()
        field.addBrowseFolderListener(
            project,
            FileChooserDescriptorFactory.singleFile()
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
            FileChooserDescriptorFactory.singleFile()
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

    private fun logLevelFragment(): SettingsEditorFragment<AspireCliRunConfiguration, LabeledComponent<ComboBox<AspireCliLogLevel>>> {
        val comboBox = ComboBox(AspireCliLogLevel.entries.toTypedArray())
        val component = labeled(comboBox, AspireCoreBundle.message("run.editor.cli.log.level"))

        val fragment = SettingsEditorFragment(
            "aspire.cli.log.level",
            AspireCoreBundle.message("run.editor.cli.log.level.name"),
            AspireCoreBundle.message("run.editor.cli.group"),
            component,
            SettingsEditorFragmentType.EDITOR,
            { config: AspireCliRunConfiguration, c: LabeledComponent<ComboBox<AspireCliLogLevel>> ->
                // Resetting happens before the fragment selection is restored, so the visibility
                // of the component cannot be taken into account here.
                c.component.item = config.logLevel ?: AspireCliLogLevel.Information
            },
            { config: AspireCliRunConfiguration, c: LabeledComponent<ComboBox<AspireCliLogLevel>> ->
                // A removed fragment is only hidden, and it is still applied, so a hidden
                // component means the option is not set (the same way the tags behave).
                config.logLevel = if (c.isVisible) c.component.item else null
            },
            { config: AspireCliRunConfiguration -> config.logLevel != null }
        )
        fragment.setActionHint(AspireCoreBundle.message("run.editor.cli.log.level.hint"))
        return fragment
    }

    private fun noBuildTag() = cliFlagTag(
        "aspire.cli.no.build",
        AspireCoreBundle.message("run.editor.cli.no.build"),
        AspireCoreBundle.message("run.editor.cli.no.build.hint"),
        { config -> config.noBuild },
        { config, value -> config.noBuild = value }
    )

    private fun isolatedTag() = cliFlagTag(
        "aspire.cli.isolated",
        AspireCoreBundle.message("run.editor.cli.isolated"),
        AspireCoreBundle.message("run.editor.cli.isolated.hint"),
        { config -> config.isolated },
        { config, value -> config.isolated = value }
    )

    private fun waitForDebuggerTag() = cliFlagTag(
        "aspire.cli.wait.for.debugger",
        AspireCoreBundle.message("run.editor.cli.wait.for.debugger"),
        AspireCoreBundle.message("run.editor.cli.wait.for.debugger.hint"),
        { config -> config.waitForDebugger },
        { config, value -> config.waitForDebugger = value }
    )

    private fun cliFlagTag(
        id: String,
        @Nls name: String,
        @Nls hint: String,
        getter: (AspireCliRunConfiguration) -> Boolean,
        setter: (AspireCliRunConfiguration, Boolean) -> Unit
    ): SettingsEditorFragment<AspireCliRunConfiguration, *> =
        SettingsEditorFragment.createTag(
            id,
            name,
            AspireCoreBundle.message("run.editor.cli.group"),
            getter,
            setter
        ).apply { setActionHint(hint) }

    private fun <T : JComponent> labeled(component: T, label: String): LabeledComponent<T> =
        LabeledComponent.create(component, label).apply { labelLocation = BorderLayout.WEST }
}
