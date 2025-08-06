@file:Suppress("LoggingSimilarMessage")

package com.jetbrains.rider.aspire.unitTests

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.application
import com.jetbrains.rd.framework.impl.RdTask
import com.jetbrains.rd.platform.util.idea.LifetimedService
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.threading.coroutines.lifetimedCoroutineScope
import com.jetbrains.rider.aspire.generated.*
import com.jetbrains.rider.aspire.worker.AspireWorkerManager
import com.jetbrains.rider.aspire.util.DCP_INSTANCE_ID_PREFIX
import com.jetbrains.rider.aspire.util.DEBUG_SESSION_PORT
import com.jetbrains.rider.aspire.util.DEBUG_SESSION_TOKEN
import com.jetbrains.rider.aspire.util.generateDcpInstancePrefix
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.toTypedArray
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

@Service(Service.Level.PROJECT)
class AspireUnitTestService(private val project: Project, private val scope: CoroutineScope) : LifetimedService() {
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
            val response = StartAspireHostResponse(existingAspireHost.environmentVariables.toTypedArray())
            rdTask.set(response)
            return
        }

        scope.launch(Dispatchers.Default) {
            lifetimedCoroutineScope(lifetime) {
                LOG.trace("Starting an Aspire host for a unit test session")
                val aspireWorker = AspireWorkerManager.getInstance(project).getOrStartAspireWorker()

                val debugSessionToken = requireNotNull(aspireWorker.debugSessionToken)
                val debugSessionPort = requireNotNull(aspireWorker.debugSessionPort)
                val debugSessionUrl = "localhost:$debugSessionPort"
                val dcpInstancePrefix = generateDcpInstancePrefix()
                val aspireHostProjectPath = Path(request.aspireHostProjectPath)

                val aspireHostConfig = AspireHostModelConfig(
                    dcpInstancePrefix,
                    null,
                    aspireHostProjectPath.absolutePathString(),
                    null,
                    null,
                    null,
                    request.underDebugger,
                    null
                )

                val environmentVariables = listOf(
                    AspireHostEnvironmentVariable(
                        DEBUG_SESSION_TOKEN,
                        debugSessionToken
                    ),
                    AspireHostEnvironmentVariable(
                        DEBUG_SESSION_PORT,
                        debugSessionUrl
                    ),
                    AspireHostEnvironmentVariable(
                        DCP_INSTANCE_ID_PREFIX,
                        dcpInstancePrefix
                    )
                )

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
        val aspireHost = aspireUnitTestHosts.remove(request.unitTestRunId)
        if (aspireHost == null) {
            LOG.info("Unable to find Aspire host for unitTestRunId ${request.unitTestRunId}")
            rdTask.set(Unit)
            return
        }
        val aspireWorker = AspireWorkerManager.getInstance(project).aspireWorker
        application.invokeLater {
            aspireWorker.stopAspireHostModel(aspireHost.aspireHostId)
        }
        rdTask.set(Unit)
    }

    fun stopAspireHost(unitTestRunId: String) {
        val aspireHost = aspireUnitTestHosts.remove(unitTestRunId)
        if (aspireHost == null) {
            LOG.info("Unable to find Aspire host for unitTestRunId $unitTestRunId")
            return
        }
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