package com.jetbrains.rider.aspire.run.host

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.project.Project
import com.jetbrains.rider.run.configurations.IAutoSelectableRunConfiguration
import com.jetbrains.rider.run.configurations.IProjectBasedRunConfiguration
import com.jetbrains.rider.run.configurations.RiderAsyncRunConfiguration
import org.jdom.Element

class AspireHostConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String,
    val parameters: AspireHostConfigurationParameters
) : RiderAsyncRunConfiguration(
    name,
    project,
    factory,
    { AspireHostConfigurationSettingsEditor(it) },
    AspireHostExecutorFactory(project, parameters)
), IProjectBasedRunConfiguration, IAutoSelectableRunConfiguration {
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
        val newConfiguration = AspireHostConfiguration(
            project,
            requireNotNull(factory),
            name,
            parameters.copy()
        )
        newConfiguration.doCopyOptionsFrom(this)
        return newConfiguration
    }

    override fun getAutoSelectPriority() = 10

    override fun getProjectFilePath() = parameters.projectFilePath

    override fun setProjectFilePath(path: String) {
        parameters.projectFilePath = path
    }
}