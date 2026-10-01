package com.jetbrains.aspire.run

import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.AspireCoreBundle
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
internal class AspireRunConfigurationManager(private val project: Project) {
    companion object {
        fun getInstance(project: Project): AspireRunConfigurationManager = project.service()
        private val LOG = logger<AspireRunConfigurationManager>()
    }

    private val runConfigurationNames = ConcurrentHashMap<Path, String>()

    fun saveRunConfigurationForAppHost(appHostFile: Path, runConfigurationName: String) {
        runConfigurationNames[appHostFile] = runConfigurationName
    }

    fun executeConfigurationForAppHost(appHostFile: Path, underDebug: Boolean) {
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


        val firstConfiguration = configurations.first()
        ProgramRunnerUtil.executeConfiguration(firstConfiguration, executor)
    }

    fun stopConfigurationForAppHost(appHostFile: Path) {
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

        Notification(
            "Aspire",
            AspireCoreBundle.message("notification.aspire.run.configurations.stopped.title"),
            AspireCoreBundle.message("notification.aspire.run.configurations.stopped.content", appHostFile.toString()),
            NotificationType.INFORMATION
        ).notify(project)
    }
}
