package me.rafaelldi.aspire.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.project.Project
import com.jetbrains.rider.build.tasks.SolutionRunConfiguration
import com.jetbrains.rider.run.configurations.IAutoSelectableRunConfiguration
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
    { AspireHostSettingsEditor(it) },
    AspireHostExecutorFactory(project, parameters)
), SolutionRunConfiguration, IAutoSelectableRunConfiguration {
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
}