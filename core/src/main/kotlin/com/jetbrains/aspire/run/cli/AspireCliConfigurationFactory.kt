package com.jetbrains.aspire.run.cli

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.AspireCoreBundle
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class AspireCliConfigurationFactory(type: ConfigurationType) : ConfigurationFactory(type) {
    companion object {
        private const val FACTORY_ID = "Aspire CLI"
    }

    override fun getId(): String = FACTORY_ID

    override fun createTemplateConfiguration(project: Project): RunConfiguration =
        AspireCliRunConfiguration(project, this, AspireCoreBundle.message("run.configuration.cli.name"))

    override fun getOptionsClass(): Class<out BaseState> = AspireCliRunConfigurationOptions::class.java
}
