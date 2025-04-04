@file:Suppress("UnstableApiUsage")

package com.jetbrains.rider.aspire.sessionHost

import com.intellij.execution.RunManager
import com.intellij.execution.RunManagerListener
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.services.ServiceEventListener
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.jetbrains.rd.platform.util.idea.LifetimedService
import com.jetbrains.rider.aspire.run.AspireHostConfiguration
import com.jetbrains.rider.aspire.run.AspireHostConfigurationType
import com.jetbrains.rider.aspire.services.AspireMainServiceViewContributor
import com.jetbrains.rider.aspire.services.SessionHost
import kotlinx.coroutines.CoroutineScope
import kotlin.io.path.Path

@Service(Service.Level.PROJECT)
class SessionHostManager(private val project: Project, scope: CoroutineScope) : LifetimedService() {
    companion object {
        fun getInstance(project: Project) = project.service<SessionHostManager>()

        private val LOG = logger<SessionHostManager>()
    }

    val sessionHost: SessionHost = SessionHost(serviceLifetime, scope.childScope("Aspire Session Host"), project)

    init {
        Disposer.register(this, sessionHost)
    }

    fun getSessionHosts(): List<SessionHost> {
        if (!sessionHost.hasAspireHosts) return emptyList()
        return listOf(sessionHost)
    }

    suspend fun getOrStartSessionHost(): SessionHost {
        LOG.trace("Starting session host")
        sessionHost.start()
        return sessionHost
    }

    suspend fun stopSessionHost() {
        LOG.trace("Stopping session host")
        sessionHost.stop()
    }

    fun addAspireHost(projectFilePath: String) {
        val hasAspireHosts = sessionHost.hasAspireHosts
        sessionHost.addAspireHostProject(Path(projectFilePath))
        if (!hasAspireHosts) {
            sendServiceResetEvent()
        }
    }

    fun removeAspireHost(projectFilePath: String) {
        val configurationType = ConfigurationTypeUtil.findConfigurationType(AspireHostConfigurationType::class.java)
        val configurations = RunManager.getInstance(project)
            .getConfigurationsList(configurationType)
            .filter { it is AspireHostConfiguration && it.parameters.projectFilePath == projectFilePath }
        if (configurations.isNotEmpty()) return

        sessionHost.removeAspireHostProject(Path(projectFilePath))
        if (!sessionHost.hasAspireHosts) {
            sendServiceResetEvent()
        }
    }

    private fun sendServiceResetEvent() {
        val event = ServiceEventListener.ServiceEvent.createResetEvent(AspireMainServiceViewContributor::class.java)
        project.messageBus.syncPublisher(ServiceEventListener.TOPIC).handle(event)
    }

    class RunListener(private val project: Project) : RunManagerListener {
        override fun runConfigurationAdded(settings: RunnerAndConfigurationSettings) {
            val configuration = settings.configuration
            if (configuration !is AspireHostConfiguration) return

            val params = configuration.parameters
            getInstance(project).addAspireHost(params.projectFilePath)
        }

        override fun runConfigurationRemoved(settings: RunnerAndConfigurationSettings) {
            val configuration = settings.configuration
            if (configuration !is AspireHostConfiguration) return

            val params = configuration.parameters
            getInstance(project).removeAspireHost(params.projectFilePath)
        }
    }
}