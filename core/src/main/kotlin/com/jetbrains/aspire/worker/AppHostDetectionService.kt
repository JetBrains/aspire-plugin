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

    fun removeAppHost(appHostFile: Path) {
        if (hasAppHost(appHostFile)) return

        project.messageBus
            .syncPublisher(AppHostDetectionListener.TOPIC)
            .appHostRemoved(appHostFile)
    }

    private fun hasAppHost(appHostFile: Path): Boolean =
        hasConfigurationFor(appHostFile) || AppHostDetectionExtension.hasAppHost(project, appHostFile)

    private fun hasConfigurationFor(appHostFile: Path): Boolean =
        RunManager.getInstance(project)
            .allConfigurationsList
            .any { it is AspireRunConfiguration && it.appHostFile == appHostFile }
}
