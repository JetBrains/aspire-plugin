package com.jetbrains.rider.aspire.run.file

import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.RunConfigurationSingletonPolicy
import com.intellij.openapi.project.Project
import com.jetbrains.rider.aspire.run.AspireConfigurationType
import com.jetbrains.rider.aspire.run.host.AspireHostConfiguration
import com.jetbrains.rider.run.configurations.DotNetConfigurationFactoryBase

internal class AspireFileConfigurationFactory(type: AspireConfigurationType) :
    DotNetConfigurationFactoryBase<AspireHostConfiguration>(type) {
    override fun getId() = "Aspire File"

    override fun getName() = "Single-file AppHost"

    override fun getSingletonPolicy() = RunConfigurationSingletonPolicy.SINGLE_INSTANCE

    override fun createTemplateConfiguration(project: Project) = AspireFileConfiguration(
        project,
        this,
        "Aspire Host",
        AspireFileConfigurationParameters.createDefault(project)
    )

    override fun configureDefaultSettings(settings: RunnerAndConfigurationSettings) {
        super.configureDefaultSettings(settings)
        settings.isActivateToolWindowBeforeRun = false
        settings.isFocusToolWindowBeforeRun = false
    }
}