package com.jetbrains.aspire.worker

import com.intellij.execution.RunManagerListener
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.run.AspireRunConfiguration

internal class AspireRunConfigurationListener(private val project: Project) : RunManagerListener {
    override fun runConfigurationAdded(settings: RunnerAndConfigurationSettings) {
        val configuration = settings.configuration
        if (configuration !is AspireRunConfiguration) return

        val mainFilePath = configuration.parameters.mainFilePath
        AspireWorkerManager
            .getInstance(project)
            .addAspireHost(mainFilePath)
    }

    override fun runConfigurationRemoved(settings: RunnerAndConfigurationSettings) {
        val configuration = settings.configuration
        if (configuration !is AspireRunConfiguration) return

        val mainFilePath = configuration.parameters.mainFilePath
        AspireWorkerManager
            .getInstance(project)
            .removeAspireHost(mainFilePath)
    }
}