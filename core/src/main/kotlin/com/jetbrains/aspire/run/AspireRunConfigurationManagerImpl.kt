package com.jetbrains.aspire.run

import com.intellij.execution.ExecutionListener
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.AspireCoreBundle
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * Remembers the names of run configurations launched for each AppHost and manages running and stopping their configurations.
 */
@ApiStatus.Internal
internal class AspireRunConfigurationManagerImpl(private val project: Project) : AspireRunConfigurationManager {
    companion object {
        private val LOG = logger<AspireRunConfigurationManagerImpl>()
    }

    private val runConfigurationNames = ConcurrentHashMap<Path, String>()

    override fun saveRunConfigurationForAppHost(appHostFile: Path, runConfigurationName: String) {
        runConfigurationNames[appHostFile] = runConfigurationName
    }

    override fun getRunConfigurationNameForAppHost(appHostFile: Path): String? = runConfigurationNames[appHostFile]

    override fun executeConfigurationForAppHost(appHostFile: Path, underDebug: Boolean) {
        val executor =
            if (underDebug) DefaultDebugExecutor.getDebugExecutorInstance()
            else DefaultRunExecutor.getRunExecutorInstance()

        val runManager = RunManager.getInstance(project)
        val selected = runManager.selectedConfiguration
        val selectedConfiguration = selected?.configuration
        if (selectedConfiguration != null && selectedConfiguration is AspireRunConfiguration) {
            if (appHostFile == selectedConfiguration.appHostFile) {
                ProgramRunnerUtil.executeConfiguration(selected, executor)
                return
            }
        }

        val configurations = runManager.allSettings.filter {
            val configuration = it.configuration
            configuration is AspireRunConfiguration && configuration.appHostFile == appHostFile
        }

        if (configurations.isEmpty()) {
            LOG.warn("Unable to find any Aspire run configurations with the given host path")
            Notification(
                "Aspire",
                AspireCoreBundle.message("notification.unable.to.find.app.host.run.config.title"),
                AspireCoreBundle.message("notification.unable.to.find.app.host.run.config.description"),
                NotificationType.WARNING
            )
                .notify(project)
            return
        }

        val savedConfigurationName = runConfigurationNames[appHostFile]
        val configurationToRun = configurations.firstOrNull { it.name == savedConfigurationName } ?: configurations.first()
        ProgramRunnerUtil.executeConfiguration(configurationToRun, executor)
    }

    override fun stopConfigurationForAppHost(appHostFile: Path) {
        val executionManager = ExecutionManagerImpl.getInstance(project)
        val descriptors = executionManager.getDescriptors { settings ->
            val configuration = settings.configuration
            configuration is AspireRunConfiguration && configuration.appHostFile == appHostFile
        }

        descriptors.forEach { descriptor ->
            if (ExecutionManagerImpl.isProcessRunning(descriptor)) {
                ExecutionManagerImpl.stopProcess(descriptor)
            }
        }
    }

    private class Listener(private val project: Project) : ExecutionListener {
        override fun processStarted(
            executorId: String,
            env: ExecutionEnvironment,
            handler: ProcessHandler
        ) {
            val profile = env.runProfile as? AspireRunConfiguration ?: return
            val appHostFile = profile.appHostFile ?: return

            project.service<AspireRunConfigurationManager>()
                .saveRunConfigurationForAppHost(appHostFile, profile.name)
        }
    }
}
