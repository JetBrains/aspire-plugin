package com.jetbrains.aspire.run

import com.intellij.execution.RunManagerListener
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.worker.AppHostDetectionService
import kotlin.io.path.nameWithoutExtension

internal class AspireRunConfigurationListener(private val project: Project) : RunManagerListener {
    override fun runConfigurationAdded(settings: RunnerAndConfigurationSettings) {
        val configuration = settings.configuration as? AspireRunConfiguration ?: return
        val appHostFilePath = configuration.appHostFilePath?.takeIf { it.toString().isNotBlank() } ?: return

        AppHostDetectionService
            .getInstance(project)
            .addAppHost(appHostFilePath.nameWithoutExtension, appHostFilePath)
    }

    override fun runConfigurationRemoved(settings: RunnerAndConfigurationSettings) {
        val configuration = settings.configuration as? AspireRunConfiguration ?: return
        val appHostFilePath = configuration.appHostFilePath?.takeIf { it.toString().isNotBlank() } ?: return

        AppHostDetectionService
            .getInstance(project)
            .removeAppHost(appHostFilePath)
    }
}
