package com.jetbrains.aspire.run.cli

import com.intellij.execution.RunManager
import com.intellij.execution.RunManagerListener
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.worker.AppHostDetectionListener
import kotlin.io.path.Path
import kotlin.io.path.nameWithoutExtension

/**
 * Publishes AppHost detection events for [AspireCliRunConfiguration]s so the Services tool window shows a
 * node for each configured AppHost even before it is run, and keeps the unreachable-breakpoint markers of
 * [AspireCliUnreachableBreakpointService] in sync with the selected configuration.
 */
internal class AspireCliRunConfigurationListener(private val project: Project) : RunManagerListener {
    override fun runConfigurationSelected(settings: RunnerAndConfigurationSettings?) {
        AspireCliUnreachableBreakpointService.getInstance(project).scheduleRefresh()
    }

    override fun runConfigurationChanged(settings: RunnerAndConfigurationSettings) {
        // The AppHost path of an existing configuration can be edited in place, which moves the boundary.
        AspireCliUnreachableBreakpointService.getInstance(project).scheduleRefresh()
    }

    override fun stateLoaded(runManager: RunManager, isFirstLoadState: Boolean) {
        // Covers the common case of an Aspire (CLI) configuration that is already selected when the IDE
        // starts, so its breakpoints get marked without any user interaction. `RunManagerImpl` also fires
        // `runConfigurationSelected` on this path; the refresh is debounced, so the two collapse into one.
        AspireCliUnreachableBreakpointService.getInstance(project).scheduleRefresh()
    }

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
