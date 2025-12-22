package com.jetbrains.aspire.rider.run

import com.intellij.execution.RunManager
import com.intellij.execution.RunManagerListener
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.worker.AspireWorker.Companion.getInstance
import java.nio.file.Path

internal class AspireRunConfigurationListener(private val project: Project) : RunManagerListener {
    override fun runConfigurationAdded(settings: RunnerAndConfigurationSettings) {
        val configuration = settings.configuration
        if (configuration !is AspireRunConfiguration) return

        val mainFilePath = configuration.parameters.mainFilePath

        getInstance(project).addAspireAppHost(Path.of(mainFilePath))
    }

    override fun runConfigurationRemoved(settings: RunnerAndConfigurationSettings) {
        val configuration = settings.configuration
        if (configuration !is AspireRunConfiguration) return

        val mainFilePath = configuration.parameters.mainFilePath

        val configurations = getAspireRunConfigurationsByMainFilePath(mainFilePath)
        if (configurations.isNotEmpty()) return

        getInstance(project).removeAspireAppHost(Path.of(mainFilePath))
    }

    private fun getAspireRunConfigurationsByMainFilePath(mainFilePath: String): List<AspireRunConfiguration> {
        val configurationType = ConfigurationTypeUtil.findConfigurationType(AspireConfigurationType::class.java)
        return RunManager.getInstance(project)
            .getConfigurationsList(configurationType)
            .filterIsInstance<AspireRunConfiguration>()
            .filter { it.parameters.mainFilePath == mainFilePath }
    }
}