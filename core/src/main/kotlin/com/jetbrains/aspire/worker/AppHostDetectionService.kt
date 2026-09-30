package com.jetbrains.aspire.worker

import com.intellij.execution.RunManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.extensions.AppHostDetectionExtension
import com.jetbrains.aspire.run.AspireRunConfiguration
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class AppHostDetectionService(private val project: Project) {
    companion object {
        fun getInstance(project: Project): AppHostDetectionService = project.service()
    }

    fun addAppHost(appHostName: String, appHostFilePath: Path) {
        project.messageBus
            .syncPublisher(AppHostDetectionListener.TOPIC)
            .appHostDetected(appHostName, appHostFilePath)
    }

    fun removeAppHost(appHostFilePath: Path) {
        if (hasAppHost(appHostFilePath)) return

        project.messageBus
            .syncPublisher(AppHostDetectionListener.TOPIC)
            .appHostRemoved(appHostFilePath)
    }

    private fun hasAppHost(appHostFilePath: Path): Boolean =
        hasConfigurationFor(appHostFilePath) || AppHostDetectionExtension.hasAppHost(project, appHostFilePath)

    private fun hasConfigurationFor(appHostFilePath: Path): Boolean =
        RunManager.getInstance(project)
            .allConfigurationsList
            .any { it is AspireRunConfiguration && it.appHostFilePath == appHostFilePath }
}
