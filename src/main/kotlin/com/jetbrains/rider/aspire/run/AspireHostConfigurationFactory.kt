package com.jetbrains.rider.aspire.run

import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.RunConfigurationSingletonPolicy
import com.intellij.openapi.project.Project
import com.jetbrains.rider.run.configurations.DotNetConfigurationFactoryBase

class AspireHostConfigurationFactory(type: AspireHostConfigurationType) :
    DotNetConfigurationFactoryBase<AspireHostConfiguration>(type) {
    override fun getId() = "Aspire Host"

    override fun getSingletonPolicy() = RunConfigurationSingletonPolicy.SINGLE_INSTANCE

    override fun createTemplateConfiguration(project: Project) = AspireHostConfiguration(
        project,
        this,
        "Aspire Host",
        AspireHostConfigurationParameters.createDefault(project)
    )

    override fun configureDefaultSettings(settings: RunnerAndConfigurationSettings) {
        super.configureDefaultSettings(settings)
        settings.isActivateToolWindowBeforeRun = false
        settings.isFocusToolWindowBeforeRun = false
    }
}