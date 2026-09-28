package com.jetbrains.aspire.run

import com.intellij.execution.RunManager
import com.intellij.execution.RunManagerListener
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.worker.AppHostDetectionListener
import java.nio.file.Path
import kotlin.io.path.nameWithoutExtension

internal class AspireRunConfigurationListener(private val project: Project) : RunManagerListener {
    override fun runConfigurationAdded(settings: RunnerAndConfigurationSettings) {
        val configuration = settings.configuration as? AspireRunConfiguration ?: return
        val appHostFilePath = configuration.appHostFilePath?.takeIf { it.toString().isNotBlank() } ?: return

        project.messageBus
            .syncPublisher(AppHostDetectionListener.TOPIC)
            .appHostDetected(appHostFilePath.nameWithoutExtension, appHostFilePath)
    }

    override fun runConfigurationRemoved(settings: RunnerAndConfigurationSettings) {
        val configuration = settings.configuration as? AspireRunConfiguration ?: return
        val appHostFilePath = configuration.appHostFilePath?.takeIf { it.toString().isNotBlank() } ?: return

        if (hasConfigurationFor(appHostFilePath)) return

        project.messageBus
            .syncPublisher(AppHostDetectionListener.TOPIC)
            .appHostRemoved(appHostFilePath)
    }

    private fun hasConfigurationFor(appHostFilePath: Path): Boolean =
        RunManager.getInstance(project)
            .allConfigurationsList
            .any { it is AspireRunConfiguration && it.appHostFilePath == appHostFilePath }
}
