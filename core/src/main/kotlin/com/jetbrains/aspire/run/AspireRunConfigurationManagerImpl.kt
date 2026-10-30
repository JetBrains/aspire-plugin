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
import com.jetbrains.aspire.run.AspireExecutionListener.Companion.APP_HOST_ID_KEY
import com.jetbrains.aspire.worker.AspireAppHostId
import com.jetbrains.aspire.worker.AspireWorker
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap

/**
 * Remembers the names of run configurations launched for each AppHost and manages running and stopping their configurations.
 */
@ApiStatus.Internal
internal class AspireRunConfigurationManagerImpl(private val project: Project) : AspireRunConfigurationManager {
    companion object {
        private val LOG = logger<AspireRunConfigurationManagerImpl>()
    }

    private val runConfigurationNames = ConcurrentHashMap<AspireAppHostId, String>()

    override fun saveRunConfigurationForAppHost(appHostId: AspireAppHostId, runConfigurationName: String) {
        runConfigurationNames[appHostId] = runConfigurationName
    }

    override fun getRunConfigurationNameForAppHost(appHostId: AspireAppHostId): String? = runConfigurationNames[appHostId]

    override fun executeConfigurationForAppHost(appHostId: AspireAppHostId, underDebug: Boolean) {
        val appHost = AspireWorker.getInstance(project).getAppHostById(appHostId) ?: return

        val executor =
            if (underDebug) DefaultDebugExecutor.getDebugExecutorInstance()
            else DefaultRunExecutor.getRunExecutorInstance()

        val runManager = RunManager.getInstance(project)
        val selected = runManager.selectedConfiguration
        val selectedConfiguration = selected?.configuration
        if (selectedConfiguration != null && selectedConfiguration is AspireRunConfiguration) {
            if (appHost.appHostPath == selectedConfiguration.appHostFile) {
                ProgramRunnerUtil.executeConfiguration(selected, executor)
                return
            }
        }

        val configurations = runManager.allSettings.filter {
            val configuration = it.configuration
            configuration is AspireRunConfiguration && configuration.appHostFile == appHost.appHostPath
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

        val savedConfigurationName = runConfigurationNames[appHostId]
        val configurationToRun = configurations.firstOrNull { it.name == savedConfigurationName } ?: configurations.first()
        ProgramRunnerUtil.executeConfiguration(configurationToRun, executor)
    }

    override fun stopConfigurationForAppHost(appHostId: AspireAppHostId) {
        val appHost = AspireWorker.getInstance(project).getAppHostById(appHostId) ?: return

        val executionManager = ExecutionManagerImpl.getInstance(project)
        val descriptors = executionManager.getDescriptors { settings ->
            val configuration = settings.configuration
            configuration is AspireRunConfiguration && configuration.appHostFile == appHost.appHostPath
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
            val appHostId = env.getUserData(APP_HOST_ID_KEY) ?: return

            project.service<AspireRunConfigurationManager>()
                .saveRunConfigurationForAppHost(appHostId, profile.name)
        }
    }
}
