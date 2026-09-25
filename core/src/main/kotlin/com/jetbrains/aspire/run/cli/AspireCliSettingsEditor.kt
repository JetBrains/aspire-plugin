package com.jetbrains.aspire.run.cli

import com.intellij.execution.configuration.EnvironmentVariablesComponent
import com.intellij.execution.ui.CommonParameterFragments
import com.intellij.execution.ui.RunConfigurationFragmentedEditor
import com.intellij.execution.ui.SettingsEditorFragment
import com.intellij.execution.ui.SettingsEditorFragmentType
import com.intellij.openapi.externalSystem.service.execution.configuration.fragments.SettingsEditorFragmentContainer
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.LabeledComponent
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.Predicates
import com.intellij.ui.components.JBTextField
import com.jetbrains.aspire.AspireCoreBundle
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import javax.swing.JComponent

internal class AspireCliSettingsEditor(private val configuration: AspireCliRunConfiguration) :
    RunConfigurationFragmentedEditor<AspireCliRunConfiguration>(configuration) {

    override fun createRunFragments(): List<SettingsEditorFragment<AspireCliRunConfiguration, *>> =
        SettingsEditorFragmentContainer.fragments {
            add(CommonParameterFragments.createHeader(AspireCoreBundle.message("run.configuration.cli.run.aspire.host")))

            add(appHostFragment())
            add(workingDirectoryFragment())
            add(createEnvironmentVariablesFragment())

            add(startBrowserTag())
            add(browserUrlFragment())
            add(logLevelFragment())
            add(podmanRuntimeTag())
            add(noBuildTag())
            add(isolatedTag())
        }

    private fun appHostFragment(): SettingsEditorFragment<AspireCliRunConfiguration, LabeledComponent<TextFieldWithBrowseButton>> {
        val field = TextFieldWithBrowseButton().apply {
            val descriptor = FileChooserDescriptorFactory
                .singleFile()
                .withTitle(AspireCoreBundle.message("run.editor.cli.app.host.title"))
                .withEnvironmentRestricted(true)
            addBrowseFolderListener(project, descriptor)
        }
        val component = labeled(field, AspireCoreBundle.message("run.editor.cli.app.host"))
        CommonParameterFragments.setMonospaced(component.component.textField)

        return SettingsEditorFragment<AspireCliRunConfiguration, LabeledComponent<TextFieldWithBrowseButton>>(
            "aspire.cli.app.host",
            AspireCoreBundle.message("run.editor.cli.app.host.name"),
            null,
            component,
            SettingsEditorFragmentType.EDITOR,
            { config, field ->
                field.component.text = config.cliOptions.appHostFilePath.orEmpty()
            },
            { config, field ->
                config.cliOptions.appHostFilePath = field.component.text.takeIf { it.isNotBlank() }
            },
            Predicates.alwaysTrue()
        ).apply {
            isRemovable = false
            setHint(AspireCoreBundle.message("run.editor.cli.app.host.hint"))
        }
    }

    private fun workingDirectoryFragment(): SettingsEditorFragment<AspireCliRunConfiguration, LabeledComponent<TextFieldWithBrowseButton>> {
        val field = TextFieldWithBrowseButton().apply {
            val descriptor = FileChooserDescriptorFactory
                .singleDir()
                .withTitle(AspireCoreBundle.message("run.editor.cli.working.directory.title"))
                .withEnvironmentRestricted(true)
            addBrowseFolderListener(project, descriptor, TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT)
        }
        val component = labeled(field, AspireCoreBundle.message("run.editor.cli.working.directory"))
        CommonParameterFragments.setMonospaced(component.component.textField)

        return SettingsEditorFragment<AspireCliRunConfiguration, LabeledComponent<TextFieldWithBrowseButton>>(
            "aspire.cli.working.directory",
            AspireCoreBundle.message("run.editor.cli.working.directory.name"),
            null,
            component,
            SettingsEditorFragmentType.EDITOR,
            { config, field ->
                field.component.text = config.cliOptions.workingDirectory.orEmpty()
            },
            { config, field ->
                config.cliOptions.workingDirectory = field.component.text.takeIf { it.isNotBlank() }
            },
            Predicates.alwaysTrue()
        ).apply {
            isCanBeHidden = true
            setHint(AspireCoreBundle.message("run.editor.cli.working.directory.hint"))
        }
    }

    private fun createEnvironmentVariablesFragment(): SettingsEditorFragment<AspireCliRunConfiguration, EnvironmentVariablesComponent> {
        val component = EnvironmentVariablesComponent(project).apply {
            labelLocation = BorderLayout.WEST
        }
        CommonParameterFragments.setMonospaced(component.component.textField)

        return SettingsEditorFragment<AspireCliRunConfiguration, EnvironmentVariablesComponent>(
            "aspire.cli.environment.variables",
            AspireCoreBundle.message("run.editor.cli.environment.variables"),
            null,
            component,
            SettingsEditorFragmentType.EDITOR,
            { config, field ->
                field.envs = config.cliOptions.environmentVariables
                field.isPassParentEnvs = config.cliOptions.passSystemEnvironment
            },
            { config, field ->
                config.cliOptions.environmentVariables = field.envs
                config.cliOptions.passSystemEnvironment = field.isPassParentEnvs
            },
            Predicates.alwaysTrue()
        ).apply {
            isCanBeHidden = true
            setHint(AspireCoreBundle.message("run.editor.cli.environment.variables.hint"))
            actionHint = AspireCoreBundle.message("run.editor.cli.environment.variables.action.hint")
        }
    }

    private fun startBrowserTag(): SettingsEditorFragment<AspireCliRunConfiguration, *> =
        SettingsEditorFragment.createTag(
            "aspire.cli.start.browser",
            AspireCoreBundle.message("run.editor.cli.start.browser"),
            null,
            { config: AspireCliRunConfiguration -> config.cliOptions.startBrowserAfterLaunch },
            { config: AspireCliRunConfiguration, value: Boolean -> config.cliOptions.startBrowserAfterLaunch = value }
        )

    private fun browserUrlFragment(): SettingsEditorFragment<AspireCliRunConfiguration, LabeledComponent<JBTextField>> {
        val field = JBTextField()
        val component = labeled(field, AspireCoreBundle.message("run.editor.cli.browser.url"))

        return SettingsEditorFragment<AspireCliRunConfiguration, LabeledComponent<JBTextField>>(
            "aspire.cli.browser.url",
            AspireCoreBundle.message("run.editor.cli.browser.url.name"),
            null,
            component,
            SettingsEditorFragmentType.EDITOR,
            { config, field ->
                field.component.text = config.cliOptions.browserUrl.orEmpty()
            },
            { config, field ->
                config.cliOptions.browserUrl = field.component.text.takeIf { it.isNotBlank() }
            },
            { config ->
                !config.cliOptions.browserUrl.isNullOrBlank()
            }
        )
    }

    private fun podmanRuntimeTag(): SettingsEditorFragment<AspireCliRunConfiguration, *> =
        SettingsEditorFragment.createTag(
            "aspire.cli.podman.runtime",
            AspireCoreBundle.message("run.editor.cli.podman.runtime"),
            null,
            { config: AspireCliRunConfiguration -> config.cliOptions.usePodmanRuntime },
            { config: AspireCliRunConfiguration, value: Boolean -> config.cliOptions.usePodmanRuntime = value }
        ).apply {
            actionHint = AspireCoreBundle.message("run.editor.cli.podman.runtime.hit")
        }

    private fun logLevelFragment(): SettingsEditorFragment<AspireCliRunConfiguration, LabeledComponent<ComboBox<AspireCliLogLevel>>> {
        val comboBox = ComboBox(AspireCliLogLevel.entries.toTypedArray())
        val component = labeled(comboBox, AspireCoreBundle.message("run.editor.cli.log.level"))

        return SettingsEditorFragment<AspireCliRunConfiguration, LabeledComponent<ComboBox<AspireCliLogLevel>>>(
            "aspire.cli.log.level",
            AspireCoreBundle.message("run.editor.cli.log.level.name"),
            AspireCoreBundle.message("run.editor.cli.group"),
            component,
            SettingsEditorFragmentType.EDITOR,
            { config, field ->
                field.component.item = config.cliOptions.logLevel ?: AspireCliLogLevel.Information
            },
            { config, field ->
                config.cliOptions.logLevel = if (field.isVisible) field.component.item else null
            },
            { config ->
                config.cliOptions.logLevel != null
            }
        ).apply {
            actionHint = AspireCoreBundle.message("run.editor.cli.log.level.hint")
        }
    }

    private fun noBuildTag() = cliFlagTag(
        "aspire.cli.no.build",
        AspireCoreBundle.message("run.editor.cli.no.build"),
        AspireCoreBundle.message("run.editor.cli.no.build.hint"),
        { config -> config.cliOptions.noBuild },
        { config, value -> config.cliOptions.noBuild = value }
    )

    private fun isolatedTag() = cliFlagTag(
        "aspire.cli.isolated",
        AspireCoreBundle.message("run.editor.cli.isolated"),
        AspireCoreBundle.message("run.editor.cli.isolated.hint"),
        { config -> config.cliOptions.isolated },
        { config, value -> config.cliOptions.isolated = value }
    )

    private fun cliFlagTag(
        id: String,
        @Nls name: String,
        @Nls hint: String,
        getter: (AspireCliRunConfiguration) -> Boolean,
        setter: (AspireCliRunConfiguration, Boolean) -> Unit
    ): SettingsEditorFragment<AspireCliRunConfiguration, *> = SettingsEditorFragment.createTag(
        id,
        name,
        AspireCoreBundle.message("run.editor.cli.group"),
        getter,
        setter
    ).apply {
        actionHint = hint
    }

    private fun <T : JComponent> labeled(component: T, label: String): LabeledComponent<T> =
        LabeledComponent.create(component, label).apply {
            labelLocation = BorderLayout.WEST
        }
}
