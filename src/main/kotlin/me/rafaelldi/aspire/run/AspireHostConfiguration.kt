package me.rafaelldi.aspire.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.project.Project
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
    { AspireHostSettingsEditor(it) },
    AspireHostExecutorFactory(project, parameters)
), IProjectBasedRunConfiguration {
    override fun checkConfiguration() {
        parameters.validate()
    }

    override fun readExternal(element: Element) {
        parameters.readExternal(element)
    }

    override fun writeExternal(element: Element) {
        parameters.writeExternal(element)
    }

    override fun getProjectFilePath() = parameters.projectFilePath

    override fun setProjectFilePath(path: String) {
        parameters.projectFilePath = path
    }
}