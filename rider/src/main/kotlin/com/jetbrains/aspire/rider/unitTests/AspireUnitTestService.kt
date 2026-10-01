@file:Suppress("LoggingSimilarMessage")

package com.jetbrains.aspire.rider.unitTests

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.jetbrains.aspire.rider.generated.AspireHostEnvironmentVariable
import com.jetbrains.aspire.rider.generated.StartAspireHostRequest
import com.jetbrains.aspire.rider.generated.StartAspireHostResponse
import com.jetbrains.aspire.rider.generated.StopAspireHostRequest
import com.jetbrains.aspire.util.DCP_INSTANCE_ID_PREFIX
import com.jetbrains.aspire.worker.AspireWorker
import com.jetbrains.aspire.worker.dcp.toDcpEnvironmentVariables
import com.jetbrains.rd.framework.impl.RdTask
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.threading.coroutines.lifetimedCoroutineScope
import com.jetbrains.rider.ijent.extensions.toNioPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Service for managing Aspire host instances used for unit test runs in a project.
 *
 * This service provides functionality for starting and stopping Aspire hosts associated
 * with unit test executions, ensuring that environment variables and host configurations
 * are managed appropriately. Each Aspire host is uniquely identified by a `unitTestRunId`.
 */
@Service(Service.Level.PROJECT)
internal class AspireUnitTestService(private val project: Project, private val scope: CoroutineScope) {
    companion object {
        fun getInstance(project: Project): AspireUnitTestService = project.service()
        private val LOG = logger<AspireUnitTestService>()
    }

    private val aspireUnitTestHosts = ConcurrentHashMap<String, AspireHostForUnitTestRun>()

    fun startAspireHost(
        lifetime: Lifetime,
        request: StartAspireHostRequest,
        rdTask: RdTask<StartAspireHostResponse>
    ) {
        val existingAspireHost = aspireUnitTestHosts[request.unitTestRunId]
        if (existingAspireHost != null) {
            LOG.trace { "Found existing aspire host for ${request.unitTestRunId}" }
            val response = StartAspireHostResponse(existingAspireHost.environmentVariables.toTypedArray())
            rdTask.set(response)
            return
        }

        scope.launch(Dispatchers.Default) {
            lifetimedCoroutineScope(lifetime) {
                LOG.trace("Starting an Aspire host for a unit test session")
                val appHostMainFilePath = request.aspireHostProjectPath.toNioPath()
                val aspireWorker = AspireWorker.getInstance(project)
                val (appHost, endpoint) = aspireWorker.startAppHostSessionServer(appHostMainFilePath)

                val environmentVariables = buildList {
                    add(AspireHostEnvironmentVariable(DCP_INSTANCE_ID_PREFIX, appHost.dcpInstancePrefix))
                    endpoint.toDcpEnvironmentVariables().forEach { envVar ->
                        val environmentVariable = AspireHostEnvironmentVariable(envVar.key, envVar.value)
                        add(environmentVariable)
                    }
                }

                val aspireUnitTestServiceHost =
                    AspireHostForUnitTestRun(appHost.dcpInstancePrefix, environmentVariables)

                val currentAspireHost =
                    aspireUnitTestHosts.putIfAbsent(request.unitTestRunId, aspireUnitTestServiceHost)

                if (currentAspireHost == null) {
                    val response = StartAspireHostResponse(environmentVariables.toTypedArray())
                    rdTask.set(response)
                } else {
                    val response = StartAspireHostResponse(currentAspireHost.environmentVariables.toTypedArray())
                    rdTask.set(response)
                }
            }
        }
    }

    fun stopAspireHost(request: StopAspireHostRequest, rdTask: RdTask<Unit>) {
        stopAspireHost(request.unitTestRunId)
        rdTask.set(Unit)
    }

    fun stopAspireHost(unitTestRunId: String) {
        val aspireHost = aspireUnitTestHosts.remove(unitTestRunId)
        if (aspireHost == null) {
            LOG.info("Unable to find Aspire host for unitTestRunId $unitTestRunId")
            return
        }

        LOG.trace { "Stopping aspire host ${aspireHost.aspireHostId}" }
    }

    private data class AspireHostForUnitTestRun(
        val aspireHostId: String,
        val environmentVariables: List<AspireHostEnvironmentVariable>
    )
}
