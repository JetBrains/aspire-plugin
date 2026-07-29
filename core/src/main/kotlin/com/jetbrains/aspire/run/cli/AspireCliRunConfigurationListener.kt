package com.jetbrains.aspire.run.cli

import com.intellij.execution.RunManager
import com.intellij.execution.RunManagerListener
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.runConfigurationType
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.worker.AppHostDetectionListener
import kotlin.io.path.Path
import kotlin.io.path.nameWithoutExtension

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
        val configurationType = runConfigurationType<AspireCliConfigurationType>()
        return RunManager.getInstance(project)
            .getConfigurationsList(configurationType)
            .filterIsInstance<AspireCliRunConfiguration>()
            .filter { it.appHostFilePath == appHostFilePath }
    }
}
