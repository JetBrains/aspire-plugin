package com.jetbrains.aspire.rider.run

import com.intellij.execution.RunManager
import com.intellij.execution.RunManagerListener
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.worker.AppHostDetectionListener
import java.nio.file.Path
import kotlin.io.path.nameWithoutExtension

internal class AspireRunConfigurationListener(private val project: Project) : RunManagerListener {
    override fun runConfigurationAdded(settings: RunnerAndConfigurationSettings) {
        val configuration = settings.configuration
        if (configuration !is AspireRunConfiguration) return

        val appHostFilePath = Path.of(configuration.parameters.appHostFilePath)

        project.messageBus
            .syncPublisher(AppHostDetectionListener.TOPIC)
            .appHostDetected(appHostFilePath.nameWithoutExtension, appHostFilePath)
    }

    override fun runConfigurationRemoved(settings: RunnerAndConfigurationSettings) {
        val configuration = settings.configuration
        if (configuration !is AspireRunConfiguration) return

        val appHostFilePath = configuration.parameters.appHostFilePath

        val configurations = getAspireRunConfigurationsByMainFilePath(appHostFilePath)
        if (configurations.isNotEmpty()) return

        val path = Path.of(appHostFilePath)
        project.messageBus
            .syncPublisher(AppHostDetectionListener.TOPIC)
            .appHostRemoved(path)
    }

    private fun getAspireRunConfigurationsByMainFilePath(appHostFilePath: String): List<AspireRunConfiguration> {
        val configurationType = ConfigurationTypeUtil.findConfigurationType(AspireConfigurationType::class.java)
        return RunManager.getInstance(project)
            .getConfigurationsList(configurationType)
            .filterIsInstance<AspireRunConfiguration>()
            .filter { it.parameters.appHostFilePath == appHostFilePath }
    }
}