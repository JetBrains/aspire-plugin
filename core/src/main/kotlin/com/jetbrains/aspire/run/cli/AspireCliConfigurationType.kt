package com.jetbrains.aspire.run.cli

import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.SimpleConfigurationType
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NotNullLazyValue
import com.jetbrains.aspire.AspireCoreBundle
import com.jetbrains.aspire.AspireIcons

internal class AspireCliConfigurationType : SimpleConfigurationType(
    ID,
    AspireCoreBundle.message("run.configuration.cli.name"),
    AspireCoreBundle.message("run.configuration.cli.description"),
    NotNullLazyValue.createValue { AspireIcons.RunConfig }
), DumbAware {
    companion object {
        const val ID = "AspireCliConfiguration"
    }

    override fun createTemplateConfiguration(project: Project): RunConfiguration =
        AspireCliRunConfiguration(project, this, configurationTypeDescription)

    override fun isEditableInDumbMode(): Boolean = true

    override fun getOptionsClass(): Class<out BaseState> = AspireCliRunConfigurationOptions::class.java

    override fun configureDefaultSettings(settings: RunnerAndConfigurationSettings) {
        super.configureDefaultSettings(settings)
        settings.isActivateToolWindowBeforeRun = false
        settings.isFocusToolWindowBeforeRun = false
    }
}
