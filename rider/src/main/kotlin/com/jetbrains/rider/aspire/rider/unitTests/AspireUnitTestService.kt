@file:Suppress("LoggingSimilarMessage")

package com.jetbrains.rider.aspire.rider.unitTests

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.util.application
import com.jetbrains.rd.framework.impl.RdTask
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.threading.coroutines.lifetimedCoroutineScope
import com.jetbrains.rider.aspire.generated.*
import com.jetbrains.rider.aspire.util.*
import com.jetbrains.rider.aspire.worker.AspireWorkerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

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
                val aspireWorker = AspireWorkerManager.getInstance(project).startAspireWorker()

                val dcpEnvironmentVariables = aspireWorker.getEnvironmentVariablesForDcpConnection()
                val dcpInstancePrefix = generateDcpInstancePrefix()
                val aspireHostProjectPath = Path(request.aspireHostProjectPath)

                val aspireHostConfig = AspireHostModelConfig(
                    dcpInstancePrefix,
                    null,
                    aspireHostProjectPath.absolutePathString(),
                    null,
                    null,
                    null,
                    null
                )

                val environmentVariables = buildList {
                    dcpEnvironmentVariables.forEach { envVar ->
                        add(AspireHostEnvironmentVariable(envVar.key, envVar.value))
                    }
                    add(AspireHostEnvironmentVariable(DCP_INSTANCE_ID_PREFIX, dcpInstancePrefix))
                }

                val aspireUnitTestServiceHost = AspireHostForUnitTestRun(
                    aspireHostConfig.id,
                    environmentVariables
                )

                val currentAspireHost =
                    aspireUnitTestHosts.putIfAbsent(request.unitTestRunId, aspireUnitTestServiceHost)

                if (currentAspireHost == null) {
                    withContext(Dispatchers.EDT) {
                        aspireWorker.startAspireHostModel(aspireHostConfig)
                    }
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
        val aspireWorker = AspireWorkerManager.getInstance(project).aspireWorker
        application.invokeLater {
            aspireWorker.stopAspireHostModel(aspireHost.aspireHostId)
        }
    }

    private data class AspireHostForUnitTestRun(
        val aspireHostId: String,
        val environmentVariables: List<AspireHostEnvironmentVariable>
    )
}