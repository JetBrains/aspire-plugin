package com.jetbrains.aspire.run.file

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.run.AspireRunConfiguration
import com.jetbrains.rider.run.configurations.IAutoSelectableRunConfiguration
import com.jetbrains.rider.run.configurations.RiderAsyncRunConfiguration
import org.jdom.Element

internal class AspireFileConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    override val configurationName: String,
    override val parameters: AspireFileConfigurationParameters
) : RiderAsyncRunConfiguration(
    configurationName,
    project,
    factory,
    { AspireFileConfigurationSettingsEditor(it) },
    AspireFileExecutorFactory(project, parameters)
), IAutoSelectableRunConfiguration, AspireRunConfiguration {
    override fun checkConfiguration() {
        parameters.validate()
    }

    override fun readExternal(element: Element) {
        super.readExternal(element)
        parameters.readExternal(element)
    }

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        parameters.writeExternal(element)
    }

    override fun clone(): RunConfiguration {
        val newConfiguration = AspireFileConfiguration(
            project,
            requireNotNull(factory),
            name,
            parameters.copy()
        )
        newConfiguration.doCopyOptionsFrom(this)
        return newConfiguration
    }

    override fun getAutoSelectPriority() = 10
}