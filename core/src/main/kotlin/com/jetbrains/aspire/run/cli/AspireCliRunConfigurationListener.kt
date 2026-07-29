package com.jetbrains.aspire.run.cli

import com.intellij.execution.RunManager
import com.intellij.execution.RunManagerListener
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.worker.AppHostDetectionListener
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.nameWithoutExtension

/**
 * Publishes AppHost detection events for [AspireCliRunConfiguration]s so the Services tool window shows a
 * node for each configured AppHost even before it is run.
 */
internal class AspireCliRunConfigurationListener(private val project: Project) : RunManagerListener {
    override fun runConfigurationAdded(settings: RunnerAndConfigurationSettings) {
        val configuration = settings.configuration
        if (configuration !is AspireCliRunConfiguration) return

        val appHostFilePath = configuration.appHostFilePath?.takeIf { it.isNotBlank() } ?: return
        val path = Path(appHostFilePath)

        project.messageBus
            .syncPublisher(AppHostDetectionListener.TOPIC)
            .appHostDetected(path.nameWithoutExtension, path)
    }

    override fun runConfigurationRemoved(settings: RunnerAndConfigurationSettings) {
        val configuration = settings.configuration
        if (configuration !is AspireCliRunConfiguration) return

        val appHostFilePath = configuration.appHostFilePath?.takeIf { it.isNotBlank() } ?: return

        if (getConfigurationsByAppHostFilePath(appHostFilePath).isNotEmpty()) return

        project.messageBus
            .syncPublisher(AppHostDetectionListener.TOPIC)
            .appHostRemoved(Path(appHostFilePath))
    }

    private fun getConfigurationsByAppHostFilePath(appHostFilePath: String): List<AspireCliRunConfiguration> {
        val configurationType = ConfigurationTypeUtil.findConfigurationType(AspireCliConfigurationType::class.java)
            ?: return emptyList()
        return RunManager.getInstance(project)
            .getConfigurationsList(configurationType)
            .filterIsInstance<AspireCliRunConfiguration>()
            .filter { it.appHostFilePath == appHostFilePath }
    }
}
