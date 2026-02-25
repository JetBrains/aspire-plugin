package com.jetbrains.aspire.rider.run.file

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.rider.run.AspireRunConfiguration
import com.jetbrains.rd.ide.model.RdFileBasedProgramSource
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.ijent.extensions.toRdPath
import com.jetbrains.rider.run.configurations.IAutoSelectableRunConfiguration
import com.jetbrains.rider.run.configurations.RiderAsyncRunConfiguration
import com.jetbrains.rider.run.configurations.dotNetFile.FileBasedProgramProjectManager
import org.jdom.Element
import java.nio.file.Path

class AspireFileConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String,
    override val parameters: AspireFileConfigurationParameters
) : RiderAsyncRunConfiguration(
    name,
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

    @Suppress("UnstableApiUsage")
    suspend fun tryGetProjectFilePath(projectFileLifetime: Lifetime): Path? {
        val projectManager = FileBasedProgramProjectManager.getInstance(project)
        return projectManager.createProjectFile(RdFileBasedProgramSource(parameters.filePath.toRdPath()), projectFileLifetime)
    }
}