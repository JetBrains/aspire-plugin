package com.jetbrains.rider.aspire.sessionHost

import com.intellij.execution.RunManager
import com.intellij.execution.RunManagerListener
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.jetbrains.rd.platform.util.idea.LifetimedService
import com.jetbrains.rider.aspire.run.AspireHostConfiguration
import com.jetbrains.rider.aspire.run.AspireHostConfigurationType
import com.jetbrains.rider.aspire.services.SessionHost
import kotlinx.coroutines.CoroutineScope
import kotlin.io.path.Path

@Service(Service.Level.PROJECT)
class SessionHostManager2(project: Project, scope: CoroutineScope) : LifetimedService() {
    companion object {
        fun getInstance(project: Project) = project.service<SessionHostManager2>()

        private val LOG = logger<SessionHostManager2>()
    }

    val sessionHost: SessionHost = SessionHost(serviceLifetime, scope, project)

    init {
        Disposer.register(this, sessionHost)
    }

    suspend fun startSessionHost() {
        LOG.trace("Starting session host")
        sessionHost.start()
    }

    suspend fun stopSessionHost() {
        LOG.trace("Stopping session host")
        sessionHost.stop()
    }

    class RunListener(private val project: Project) : RunManagerListener {
        override fun runConfigurationAdded(settings: RunnerAndConfigurationSettings) {
            val configuration = settings.configuration
            if (configuration !is AspireHostConfiguration) return

            val params = configuration.parameters
            getInstance(project).sessionHost.addAspireHost(Path(params.projectFilePath))
        }

        override fun runConfigurationRemoved(settings: RunnerAndConfigurationSettings) {
            val configuration = settings.configuration
            if (configuration !is AspireHostConfiguration) return

            val params = configuration.parameters

            val configurationType = ConfigurationTypeUtil.findConfigurationType(AspireHostConfigurationType::class.java)
            val configurations = RunManager.getInstance(project)
                .getConfigurationsList(configurationType)
                .filter { it is AspireHostConfiguration && it.parameters.projectFilePath == params.projectFilePath }
            if (configurations.isNotEmpty()) return

            getInstance(project).sessionHost.removeAspireHost(Path(params.projectFilePath))
        }
    }
}