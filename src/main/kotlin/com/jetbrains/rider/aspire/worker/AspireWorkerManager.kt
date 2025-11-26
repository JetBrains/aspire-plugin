@file:Suppress("UnstableApiUsage")

package com.jetbrains.rider.aspire.worker

import com.intellij.execution.RunManager
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.services.ServiceEventListener
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.jetbrains.rd.platform.util.idea.LifetimedService
import com.jetbrains.rider.aspire.dashboard.AspireMainServiceViewContributor
import com.jetbrains.rider.aspire.dashboard.AspireWorker
import com.jetbrains.rider.aspire.run.AspireConfigurationType
import com.jetbrains.rider.aspire.run.AspireRunConfiguration
import kotlin.io.path.Path

/**
 * Manages the lifecycle of an `AspireWorker` within a project.
 * This service is responsible for starting, stopping, and updating the state of the `AspireWorker`.
 */
@Service(Service.Level.PROJECT)
internal class AspireWorkerManager(private val project: Project) : LifetimedService() {
    companion object {
        fun getInstance(project: Project) = project.service<AspireWorkerManager>()

        private val LOG = logger<AspireWorkerManager>()
    }

    val aspireWorker: AspireWorker = AspireWorker(serviceLifetime.createNested(), project)

    init {
        Disposer.register(this, aspireWorker)
    }

    /**
     * Starts the Aspire worker.
     *
     * @return The instance of the `AspireWorker` that has been started.
     */
    suspend fun startAspireWorker(): AspireWorker {
        LOG.trace("Starting Aspire worker")
        aspireWorker.start()
        return aspireWorker
    }

    /**
     * Stops the Aspire worker.
     */
    suspend fun stopAspireWorker() {
        LOG.trace("Stopping Aspire worker")
        aspireWorker.stop()
    }

    fun addAspireHost(projectFilePath: String) {
        val hasAspireHosts = aspireWorker.hasAspireHosts
        aspireWorker.addAspireHostProject(Path(projectFilePath))
        if (!hasAspireHosts) {
            sendServiceResetEvent()
        }
    }

    fun removeAspireHost(projectFilePath: String) {
        val configurationType = ConfigurationTypeUtil.findConfigurationType(AspireConfigurationType::class.java)
        val configurations = RunManager.getInstance(project)
            .getConfigurationsList(configurationType)
            .filterIsInstance<AspireRunConfiguration>()
            .filter { it.parameters.mainFilePath == projectFilePath }
        if (configurations.isNotEmpty()) return

        aspireWorker.removeAspireHostProject(Path(projectFilePath))
        if (!aspireWorker.hasAspireHosts) {
            sendServiceResetEvent()
        }
    }

    private fun sendServiceResetEvent() {
        val event = ServiceEventListener.ServiceEvent.createResetEvent(AspireMainServiceViewContributor::class.java)
        project.messageBus.syncPublisher(ServiceEventListener.TOPIC).handle(event)
    }
}