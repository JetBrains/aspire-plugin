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
import com.jetbrains.rider.aspire.sessionHost.SessionHostManager
import com.jetbrains.rider.aspire.util.DCP_INSTANCE_ID_PREFIX
import com.jetbrains.rider.aspire.util.DEBUG_SESSION_PORT
import com.jetbrains.rider.aspire.util.DEBUG_SESSION_TOKEN
import com.jetbrains.rider.aspire.util.generateDcpInstancePrefix
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

@Service(Service.Level.PROJECT)
class AspireUnitTestService(private val project: Project, private val scope: CoroutineScope) : LifetimedService() {
    companion object {
        fun getInstance(project: Project): AspireUnitTestService = project.service()
        private val LOG = logger<AspireUnitTestService>()
    }

    private val aspireUnitTestHosts = ConcurrentHashMap<String, AspireHostForUnitTestRun>()

    fun startSessionHost(
        lifetime: Lifetime,
        request: StartSessionHostRequest,
        rdTask: RdTask<StartSessionHostResponse>
    ) {
        val existingSessionHost = aspireUnitTestHosts[request.unitTestRunId]
        if (existingSessionHost != null) {
            val response = StartSessionHostResponse(existingSessionHost.environmentVariables.toTypedArray())
            rdTask.set(response)
            return
        }

        scope.launch(Dispatchers.Default) {
            lifetimedCoroutineScope(lifetime) {
                LOG.trace("Starting a session host for a unit test session")
                val sessionHost = SessionHostManager.getInstance(project).getOrStartSessionHost()

                val debugSessionToken = requireNotNull(sessionHost.debugSessionToken)
                val debugSessionPort = requireNotNull(sessionHost.debugSessionPort)
                val debugSessionUrl = "localhost:$debugSessionPort"
                val dcpInstancePrefix = generateDcpInstancePrefix()
                val aspireHostProjectPath = Path(request.aspireHostProjectPath)

                val aspireHostConfig = AspireHostModelConfig(
                    dcpInstancePrefix,
                    null,
                    aspireHostProjectPath.absolutePathString(),
                    null,
                    null,
                    request.underDebugger,
                    null
                )

                val environmentVariables = listOf(
                    SessionHostEnvironmentVariable(
                        DEBUG_SESSION_TOKEN,
                        debugSessionToken
                    ),
                    SessionHostEnvironmentVariable(
                        DEBUG_SESSION_PORT,
                        debugSessionUrl
                    ),
                    SessionHostEnvironmentVariable(
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
                        sessionHost.startAspireHostModel(aspireHostConfig)
                    }
                    val response = StartSessionHostResponse(environmentVariables.toTypedArray())
                    rdTask.set(response)
                } else {
                    val response = StartSessionHostResponse(currentAspireHost.environmentVariables.toTypedArray())
                    rdTask.set(response)
                }
            }
        }
    }

    fun stopSessionHost(request: StopSessionHostRequest, rdTask: RdTask<Unit>) {
        val aspireHost = aspireUnitTestHosts.remove(request.unitTestRunId)
        if (aspireHost == null) {
            LOG.info("Unable to find Aspire host for unitTestRunId ${request.unitTestRunId}")
            rdTask.set(Unit)
            return
        }
        val sessionHost = SessionHostManager.getInstance(project).sessionHost
        application.invokeLater {
            sessionHost.stopAspireHostModel(aspireHost.aspireHostId)
        }
        rdTask.set(Unit)
    }

    fun stopSessionHost(unitTestRunId: String) {
        val aspireHost = aspireUnitTestHosts.remove(unitTestRunId)
        if (aspireHost == null) {
            LOG.info("Unable to find Aspire host for unitTestRunId $unitTestRunId")
            return
        }
        val sessionHost = SessionHostManager.getInstance(project).sessionHost
        application.invokeLater {
            sessionHost.stopAspireHostModel(aspireHost.aspireHostId)
        }
    }

    private data class AspireHostForUnitTestRun(
        val aspireHostId: String,
        val environmentVariables: List<SessionHostEnvironmentVariable>
    )
}